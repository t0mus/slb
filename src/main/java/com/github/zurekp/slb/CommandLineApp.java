package com.github.zurekp.slb;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.util.Headers;

import java.net.URI;
import java.util.UUID;

public class CommandLineApp {
    public static void main(final String[] args) {
        final Undertow node1 = testNode(8001, "node1");
        final Undertow node2 = testNode(8002, "node2");
        final Undertow node3 = testNode(8003, "node3");

        node1.start();
        node2.start();
        node3.start();

        final Configuration config = new Configuration();
        config.addStickySessionCookieName("JSESSIONID");
        config.addNode(URI.create("http://localhost:8001"), "node1");
        config.addNode(URI.create("http://localhost:8002"), "node2");
        config.addNode(URI.create("http://localhost:8003"), "node3");
        final SimpleLoadBalancer loadBalancer = new SimpleLoadBalancer(config);
        loadBalancer.start();
    }

    private static Undertow testNode(final int port, final String nodeId) {
        return Undertow.builder().addHttpListener(port, "localhost").setHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                final Cookie sessionCookie = exchange.getRequestCookies().get("JSESSIONID");
                if ((sessionCookie == null) || (sessionCookie.getValue() == null) || !sessionCookie.getValue().startsWith(nodeId)) {
                    exchange.getResponseCookies().put("JSESSIONID", new CookieImpl("JSESSIONID", nodeId + UUID.randomUUID().toString()));
                }
                exchange.getResponseSender().send("Hello World from " + nodeId);
            }
        }).build();
    }
}
