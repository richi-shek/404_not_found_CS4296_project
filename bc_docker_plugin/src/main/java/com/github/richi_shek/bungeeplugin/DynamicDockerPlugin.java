package com.github.richi_shek.bungeeplugin;

import com.github.richi_shek.bungeeplugin.server.DockerServer;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import java.io.File;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class DynamicDockerPlugin extends Plugin implements Listener {

    private static DynamicDockerPlugin instance;

    public static DynamicDockerPlugin getInstance() {
        return instance;
    }
    private DockerAdapter adapter;
    private List<DockerServer> servers = new CopyOnWriteArrayList<>();
    private int K = 20;

    private Configuration configuration;

    private Map<String, DockerServer> playerToServerMap = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        loadConfig();
        String environment = getConfigValue("environment", "local");
        String imageName = getConfigValue("image", "spigot_metrics");
        int initialServers = getConfigValue("initial_servers", 2);
        K = configuration.getInt("threshold", 20);
        String accessKeyId = getConfigValue("aws_access_key_id", "");
        String secretAccessKey = getConfigValue("aws_secret_access_key", "");
        String sessionToken = getConfigValue("aws_session_token", "");
        String taskDefinition = getConfigValue("aws_task_definition", "spigot-task");
        String cluster_name = getConfigValue("aws_cluster_name", "mc-cluster");
        switch (environment) {
            case "ec2":
                if(sessionToken != null && !sessionToken.isEmpty()){
                    adapter = new EC2DockerAdapter(accessKeyId, secretAccessKey, sessionToken, cluster_name, taskDefinition);
                } else {
                    adapter = new EC2DockerAdapter(accessKeyId, secretAccessKey, cluster_name, taskDefinition);
                }
                break;
            case "fargate":
                if(sessionToken != null && !sessionToken.isEmpty()){
                    adapter = new FargateDockerAdapter(accessKeyId, secretAccessKey, sessionToken, cluster_name, taskDefinition);
                } else {
                    adapter = new FargateDockerAdapter(accessKeyId, secretAccessKey, cluster_name, taskDefinition);
                }
                break;
            default:
                adapter = new LocalDockerAdapter(imageName);
                break;
        }

        for (int i = 0; i < initialServers; i++) {
            DockerServer server = adapter.create();
            servers.add(server);
            registerServer(server);
        }

        getProxy().getPluginManager().registerListener(this, this);
    }

    private void loadConfig(){
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        try {
            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                FileOutputStream outputStream = new FileOutputStream(configFile); // Throws IOException
                InputStream in = getResourceAsStream("config.yml"); // This file must exist in the jar resources folder
                in.transferTo(outputStream); // Throws IOException
            }

            configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "config.yml"));
        } catch (IOException e) {

        }
    }

    @Override
    public void onDisable() {
        instance = null;
        servers.forEach(adapter::remove);
        adapter.destroy();
    }
    public String getConfigValue(String key, String def) {
        String value = configuration.getString(key, def);
        if (value == null || value.isEmpty()) {
            return def;
        }
        return value;
    }

    public int getConfigValue(String key, int def) {
        return configuration.getInt(key, def);
    }

    private void registerServer(DockerServer server) {
        ServerInfo info = ProxyServer.getInstance().constructServerInfo(
                server.getName(),
                new InetSocketAddress(server.getAddress(), server.getPort()),
                "Dynamic Server", false);
        ProxyServer.getInstance().getServers().put(server.getName(), info);
    }

    private void unregisterServer(DockerServer server) {
        ProxyServer.getInstance().getServers().remove(server.getName());
    }

    private void createServerAsync() {
        ProxyServer.getInstance().getScheduler().runAsync(this, () -> {
            DockerServer newServer = adapter.create();
            servers.add(newServer);
            registerServer(newServer);
        });
    }

    private void removeServerAsync(DockerServer server) {
        servers.remove(server);
        unregisterServer(server);
        ProxyServer.getInstance().getScheduler().runAsync(this, () -> {
            adapter.remove(server);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onConnect(ServerConnectEvent event) {
        Optional<DockerServer> stream = servers.stream()
                .filter(s -> s.isActive() && s.getCurrentPlayers() < s.getMaxPlayers())
                .max(Comparator.comparingInt(DockerServer::getCurrentPlayers));
        DockerServer target;

        if(stream.isPresent()) {
            target = stream.get();
        } else {
            event.getPlayer().disconnect(new TextComponent("No available servers"));
            return;
        }

        target.incrementPlayers();
        ProxiedPlayer player = event.getPlayer();
        playerToServerMap.put(player.getName(), target);
        event.setTarget(ProxyServer.getInstance().getServerInfo(target.getName()));

        int C = servers.stream().mapToInt(s -> s.getMaxPlayers() - s.getCurrentPlayers()).sum();
        if (C <= K) {
            createServerAsync();
        }
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        DockerServer server = playerToServerMap.get(event.getPlayer().getName());
        if(server == null) {
            return;
        }
        server.decrementPlayers();
        if (server.getCurrentPlayers() == 0) {
            int C = servers.stream().mapToInt(s -> s.getMaxPlayers() - s.getCurrentPlayers()).sum();
            if (C > 2 * K) {
                removeServerAsync(server);
            }
        }
        playerToServerMap.remove(event.getPlayer().getName());
    }
}
