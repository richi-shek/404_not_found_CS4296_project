package com.github.richi_shek.bungeeplugin.server;

public abstract class DockerServer {
    protected String name;
    protected String address;
    protected int port;
    protected int maxPlayers;
    protected int currentPlayers;
    protected boolean active;

    public DockerServer(String name, String address, int port, int maxPlayers) {
        this.name = name;
        this.address = address;
        this.port = port;
        this.maxPlayers = maxPlayers;
        this.currentPlayers = 0;
        this.active = true;
    }

    public String getName() { return name; }
    public String getAddress() { return address; }
    public int getPort() { return port; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getCurrentPlayers() { return currentPlayers; }
    public boolean isActive() { return active; }

    public void incrementPlayers() { currentPlayers++; }
    public void decrementPlayers() { currentPlayers = Math.max(0, currentPlayers - 1); }
    public void setActive(boolean active) { this.active = active; }
}
