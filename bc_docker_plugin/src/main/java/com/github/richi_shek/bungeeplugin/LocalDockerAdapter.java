package com.github.richi_shek.bungeeplugin;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.richi_shek.bungeeplugin.server.LocalDockerServer;
import com.github.richi_shek.bungeeplugin.server.DockerServer;

public class LocalDockerAdapter implements DockerAdapter {
    private DockerClient dockerClient;

    private int nextID = 1;

    private int nextPort = 25566;

    private String imageName;

    public LocalDockerAdapter(String imageName) {
        this.dockerClient = DockerClientBuilder.getInstance().build();
        this.imageName = imageName;
    }

    public DockerServer create() {
        CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                .withExposedPorts(new com.github.dockerjava.api.model.ExposedPort(25565))
                .withEnv("SERVER_ID=" + nextID)
                .withPortBindings(com.github.dockerjava.api.model.PortBinding.parse(nextPort + ":25565"))
                .exec();
        nextID++;

        dockerClient.startContainerCmd(container.getId()).exec();

        String serverName = "local-" + container.getId().substring(0, 6);
        DockerServer server = new LocalDockerServer(serverName, "localhost", nextPort, 20);
        nextPort++;
        return server;
    }

    public void remove(DockerServer server) {
        String containerId = server.getName().substring(6);
        dockerClient.stopContainerCmd(containerId).exec();
        dockerClient.removeContainerCmd(containerId).exec();
    }

    public void destroy() {
        try {
            dockerClient.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
