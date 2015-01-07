package com.github.zurekp.slb;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.Headers;

import static java.util.Objects.requireNonNull;

public class SimpleLoadBalancer {
    private final Undertow server;
    private final GracefulShutdownHandler shutdownHandler;

    public SimpleLoadBalancer(final Configuration configuration) {
        requireNonNull(configuration);

        shutdownHandler = Handlers.gracefulShutdown(createHandler(configuration));

        server = Undertow.builder().
                addHttpListener(configuration.getPort(), configuration.getHost()).
                setHandler(shutdownHandler).
                build();
    }

    public void start() {
        server.start();
    }

    public void stop() {
        shutdownHandler.shutdown();
        shutdownHandler.addShutdownListener(new GracefulShutdownHandler.ShutdownListener() {
            @Override
            public void shutdown(boolean shutdownSuccessful) {
                server.stop();
            }
        });
    }

    private HttpHandler createHandler(Configuration configuration) {
        PathHandler result = Handlers.path(new LoadBalancerHandler(configuration));
        result.addExactPath(configuration.getShutdownPath(), new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                stop();
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                exchange.getResponseSender().send("Shutdown has been requested.");
            }
        });
        return result;
    }
}