package com.github.richi_shek.bungeeplugin;

import com.github.richi_shek.bungeeplugin.server.DockerServer;

public interface DockerAdapter {
    DockerServer create();
    void remove(DockerServer server);
    void destroy();
}