package com.github.zurekp.slb;

import io.undertow.Undertow;

import static java.util.Objects.requireNonNull;

public class SimpleLoadBalancer {
    private final Undertow server;

    public SimpleLoadBalancer(final Configuration configuration) {
        requireNonNull(configuration);
        server = Undertow.builder().
                addHttpListener(configuration.getPort(), configuration.getHost()).
                setHandler(new LoadBalancerHandler(configuration)).
                build();
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop();
    }
}