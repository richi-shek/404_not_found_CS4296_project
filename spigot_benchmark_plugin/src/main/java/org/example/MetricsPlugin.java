package org.example;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.craftbukkit.v1_16_R3.CraftServer;
import org.bukkit.plugin.java.JavaPlugin;
import net.minecraft.server.v1_16_R3.MinecraftServer;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.concurrent.ConcurrentHashMap;

class TickListener extends BukkitRunnable {
    private final int[] indices = new int[]{0, 0, 0};
    private final double[] averages = new double[]{0d, 0d, 0d};
    private final ConcurrentHashMap<Integer, Double> durations1m = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Double> durations5m = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Double> durations15m = new ConcurrentHashMap<>();

    private long lastTickTime = -1;

    @Override
    public void run() {

        if (this.indices[0] >= 20 * 60) {
            this.indices[0] = 1;
        }
        if (this.indices[1] >= 20 * 60 * 5) {
            this.indices[1] = 1;
        }
        if (this.indices[2] >= 20 * 60 * 15) {
            this.indices[2] = 1;
        }

        if(this.lastTickTime == -1) {
            lastTickTime = System.nanoTime();
            return;
        }

        double duration = ((double) (System.nanoTime() - lastTickTime) / 1000000.0);
        lastTickTime = System.nanoTime();

        this.indices[0]++;
        this.indices[1]++;
        this.indices[2]++;

        this.durations1m.put(this.indices[0], duration);
        this.durations5m.put(this.indices[1], duration);
        this.durations15m.put(this.indices[2], duration);

        double totalOneMinute = 0d;
        double totalFiveMinutes = 0d;
        double totalFifteenMinutes = 0d;
        for (double d : this.durations1m.values()) {
            totalOneMinute += d;
        }
        for (double d : this.durations5m.values()) {
            totalFiveMinutes += d;
        }
        for (double d : this.durations15m.values()) {
            totalFifteenMinutes += d;
        }
        this.averages[0] = totalOneMinute / ((double) this.durations1m.size());
        this.averages[1] = totalFiveMinutes / ((double) this.durations5m.size());
        this.averages[2] = totalFifteenMinutes / ((double) this.durations15m.size());
    }

    public double getAverageTickTime(int index) {
        return this.averages[index];
    }
}

public class MetricsPlugin extends JavaPlugin {

    private int getServerID() {
        try {
            String serverID = System.getenv("SERVER_ID");
            if (serverID != null) {
                return Integer.parseInt(serverID);
            } else {
                return 0;
            }
        }
        catch (Exception e) {
            return 0;
        }
    }

    private TickListener listener;
    @Override
    public void onEnable() {
        listener = new TickListener();
        listener.runTaskTimer(this, 0, 1);
        PluginCommand metrics_command = this.getCommand("metrics");
        if(metrics_command == null) {
            getLogger().warning("Failed to register command");
            return;
        }
        metrics_command.setExecutor((sender, command, label, args) -> {
            MinecraftServer server = ((CraftServer)Bukkit.getServer()).getServer();
            double[] tps = server.recentTps; // 1m, 5m, 15m

            sender.sendMessage("ID: " + getServerID() + "\nTPS: " + tps[0] + "\nMSPT: " + listener.getAverageTickTime(0));
            return true;
        });
    }

    @Override
    public void onDisable() {
        listener.cancel();
    }
}