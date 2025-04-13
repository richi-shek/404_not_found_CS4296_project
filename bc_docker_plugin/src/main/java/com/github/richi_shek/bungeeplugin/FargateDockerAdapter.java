package com.github.richi_shek.bungeeplugin;

import com.github.richi_shek.bungeeplugin.server.DockerServer;
import com.github.richi_shek.bungeeplugin.server.FargateDockerServer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;

public class FargateDockerAdapter extends ECSDockerAdapter {

    public FargateDockerAdapter(String accessKeyId, String secretAccessKey, String cluster_name, String taskDefinition) {
        super(cluster_name, taskDefinition, AwsBasicCredentials.create(accessKeyId, secretAccessKey), LaunchType.FARGATE);
    }

    public FargateDockerAdapter(String accessKeyId, String secretAccessKey, String session_token, String cluster_name, String taskDefinition) {
        super(cluster_name, taskDefinition, AwsSessionCredentials.create(accessKeyId, secretAccessKey, session_token), LaunchType.FARGATE);
    }

    @Override
    public NetworkConfiguration getNetworkConfiguration() {
        return NetworkConfiguration.builder()
                .awsvpcConfiguration(AwsVpcConfiguration.builder()
                        .subnets(DynamicDockerPlugin.getInstance().getConfigValue("aws_subnet_id", "subnet-0ad26f0c01e0e1154"))
                        .securityGroups(DynamicDockerPlugin.getInstance().getConfigValue("aws_security_group_id", "sg-0efddac0bf2485bcb"))
                        .assignPublicIp(AssignPublicIp.ENABLED)
                        .build()).build();
    }
}
