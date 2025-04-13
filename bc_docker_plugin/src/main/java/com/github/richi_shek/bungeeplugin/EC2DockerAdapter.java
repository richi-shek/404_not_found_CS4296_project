package com.github.richi_shek.bungeeplugin;

import com.github.richi_shek.bungeeplugin.server.DockerServer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.services.ecs.model.AwsVpcConfiguration;
import software.amazon.awssdk.services.ecs.model.LaunchType;
import software.amazon.awssdk.services.ecs.model.NetworkConfiguration;

public class EC2DockerAdapter extends ECSDockerAdapter {

    public EC2DockerAdapter(String accessKeyId, String secretAccessKey, String cluster_name, String taskDefinition) {
        super(cluster_name, taskDefinition, AwsBasicCredentials.create(accessKeyId, secretAccessKey), LaunchType.EC2);
    }

    public EC2DockerAdapter(String accessKeyId, String secretAccessKey, String session_token, String cluster_name, String taskDefinition) {
        super(cluster_name, taskDefinition, AwsSessionCredentials.create(accessKeyId, secretAccessKey, session_token), LaunchType.EC2);
    }

    @Override
    public NetworkConfiguration getNetworkConfiguration() {
        return NetworkConfiguration.builder()
                .awsvpcConfiguration(AwsVpcConfiguration.builder()
                        .subnets(DynamicDockerPlugin.getInstance().getConfigValue("aws_subnet_id", "subnet-0ad26f0c01e0e1154"))
                        .securityGroups(DynamicDockerPlugin.getInstance().getConfigValue("aws_security_group_id", "sg-0efddac0bf2485bcb"))
                        .build()).build();
    }
}
