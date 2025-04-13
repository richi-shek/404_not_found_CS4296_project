package com.github.richi_shek.bungeeplugin.server;

public class EC2DockerServer extends DockerServer {
    public EC2DockerServer(String name, String address, int port, int maxPlayers) {
        super(name, address, port, maxPlayers);
    }
}