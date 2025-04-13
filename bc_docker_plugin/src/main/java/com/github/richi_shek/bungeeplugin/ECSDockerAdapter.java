package com.github.richi_shek.bungeeplugin;

import com.github.richi_shek.bungeeplugin.server.DockerServer;
import com.github.richi_shek.bungeeplugin.server.EC2DockerServer;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesResponse;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;

public abstract class ECSDockerAdapter implements DockerAdapter {

    private int nextID = 1;

    private int nextPort = 25566;

    protected EcsClient ecsClient;

    protected Ec2Client ec2Client;
    protected String cluster_name;
    protected String taskDefinition;

    private final LaunchType type;

    public ECSDockerAdapter(String cluster_name, String taskDefinition, AwsCredentials credentials, LaunchType type) {
        this.cluster_name = cluster_name;
        this.taskDefinition = taskDefinition;
        ecsClient = EcsClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
        ec2Client = Ec2Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
        this.type = type;
    }

    public boolean isClusterExists() {
        DescribeClustersRequest describeClustersRequest = DescribeClustersRequest.builder()
                .clusters(cluster_name)
                .build();
        DescribeClustersResponse describeClustersResponse = ecsClient.describeClusters(describeClustersRequest);
        return describeClustersResponse.clusters().stream()
                .anyMatch(c -> c.clusterName().equals(cluster_name));
    }

    public void checkClusterExists() {
        if (!isClusterExists()) {
            CreateClusterRequest createClusterRequest = CreateClusterRequest.builder()
                    .clusterName(cluster_name)
                    .build();
            ecsClient.createCluster(createClusterRequest);
        }
    }

    public abstract NetworkConfiguration getNetworkConfiguration();

    public String sendCreateRequest() {
        RunTaskResponse response = ecsClient.runTask(RunTaskRequest.builder()
                .cluster(cluster_name)
                .taskDefinition(taskDefinition)
                .launchType(type)
                .count(1)
                .overrides(TaskOverride.builder()
                        .containerOverrides(ContainerOverride.builder()
                                .name("spigot_metrics")
                                .environment(
                                        KeyValuePair.builder()
                                            .name("PORT")
                                            .value(String.valueOf(nextPort))
                                            .build(),
                                        KeyValuePair.builder()
                                                .name("FRP_PUBLIC_IP")
                                                .value(DynamicDockerPlugin.getInstance().getConfigValue("FRP_PUBLIC_IP", "0.0.0.0"))
                                                .build(),
                                        KeyValuePair.builder()
                                                .name("FRP_SERVER_PORT")
                                                .value(String.valueOf(DynamicDockerPlugin.getInstance().getConfigValue("FRP_SERVER_PORT", 7000)))
                                                .build(),
                                        KeyValuePair.builder()
                                                .name("FRP_TOKEN")
                                                .value(String.valueOf(DynamicDockerPlugin.getInstance().getConfigValue("FRP_TOKEN", "")))
                                                .build(),
                                        KeyValuePair.builder()
                                                .name("SERVER_ID")
                                                .value(String.valueOf(nextID))
                                                .build()
                                ).build()
                        ).build())
                .networkConfiguration(getNetworkConfiguration())
                .build());

        if(!response.failures().isEmpty()) {
            throw new RuntimeException("Failed to create task: " + response.failures().getFirst().reason());
        }
        nextPort++;
        nextID++;
        return response.tasks().getFirst().taskArn();
    }

    public DockerServer create() {
        checkClusterExists();
        String address = "127.0.0.1";
        String taskArn = sendCreateRequest();
        return new EC2DockerServer("ecs-" + taskArn, address, nextPort - 1, 20);
    }

    public void remove(DockerServer server) {
        ecsClient.stopTask(StopTaskRequest.builder()
                .cluster(cluster_name)
                .task(server.getName().substring(4))
                .reason("Scale down")
                .build());
    }

    public void destroy() {
        // close ECS and EC2 clients
        ecsClient.close();
        ec2Client.close();
    }
}