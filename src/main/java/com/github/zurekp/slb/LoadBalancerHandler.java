package com.github.zurekp.slb;

import io.undertow.Handlers;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyConnectionPool;
import io.undertow.util.AttachmentKey;
import io.undertow.util.ConduitFactory;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import org.xnio.conduits.StreamSinkConduit;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

class LoadBalancerHandler implements HttpHandler {
    private static final AttachmentKey<String> NODE_ID_KEY = AttachmentKey.create(String.class);

    private final StickSessionIfNeeded stickSessionIfNeeded;
    private final HttpHandler proxy;

    public LoadBalancerHandler(final Configuration configuration) {
        requireNonNull(configuration, "configuration can't be null");
        stickSessionIfNeeded = new StickSessionIfNeeded(configuration);
        proxy = Handlers.proxyHandler(new TargetProxyClient(configuration));
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        exchange.addResponseWrapper(stickSessionIfNeeded);
        proxy.handleRequest(exchange);
    }

    private static class StickSessionIfNeeded implements ConduitWrapper<StreamSinkConduit> {
        private final Set<String> stickySessionCookieNames;
        private final String nodeIdCookieName;

        public StickSessionIfNeeded(final Configuration configuration) {
            requireNonNull(configuration, "configuration can't be null");

            stickySessionCookieNames = configuration.getStickySessionCookieNames();
            nodeIdCookieName = configuration.getNodeIdCookieName();
        }

        @Override
        public StreamSinkConduit wrap(final ConduitFactory<StreamSinkConduit> factory, final HttpServerExchange exchange) {
            if (isSessionStickinessRequired(exchange)) {
                stickSessionToRespondingNode(exchange);
            }
            return factory.create();
        }

        private boolean isSessionStickinessRequired(final HttpServerExchange exchange) {
            final Map<String, Cookie> responseCookies = exchange.getResponseCookies();
            final HeaderValues setCookieHeader = exchange.getResponseHeaders().get(Headers.SET_COOKIE);
            for (final String cookieName : stickySessionCookieNames) {
                if (responseCookies.containsKey(cookieName)) return true;
                if (setCookieHeader != null) {
                    for (final String cookie : setCookieHeader) {
                        if (cookie.startsWith(cookieName + "=")) return true;
                    }
                }
            }
            return false;
        }

        private void stickSessionToRespondingNode(final HttpServerExchange exchange) {
            final String nodeId = exchange.getAttachment(NODE_ID_KEY);
            if (nodeId == null) {
                throw new IllegalStateException("Can't find a nodeId for the exchange.");
            }
            final Cookie currentNodeIdCookie = exchange.getRequestCookies().get(nodeIdCookieName);
            if (currentNodeIdCookie != null && nodeId.equals(currentNodeIdCookie.getValue())) return;
            exchange.getResponseCookies().put(nodeIdCookieName, new CookieImpl(nodeIdCookieName, nodeId));
        }
    }

    private static class TargetProxyClient extends LoadBalancingProxyClient {
        private final String nodeIdCookieName;
        private final Field routesFiled;
        private final Field hostNodeIdField;
        private final Field hostConnectionPoolField;

        public TargetProxyClient(final Configuration configuration) {
            requireNonNull(configuration, "configuration can't be null");

            nodeIdCookieName = configuration.getNodeIdCookieName();
            try {
                routesFiled = LoadBalancingProxyClient.class.getDeclaredField("routes");
                hostNodeIdField = Host.class.getDeclaredField("jvmRoute");
                hostConnectionPoolField = Host.class.getDeclaredField("connectionPool");
            } catch (final NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
            routesFiled.setAccessible(true);
            hostNodeIdField.setAccessible(true);
            hostConnectionPoolField.setAccessible(true);
            for (final String nodeId : configuration.getNodeIds()) {
                addHost(configuration.getNodeURI(nodeId), nodeId);
            }
        }

        @Override
        protected Host selectHost(final HttpServerExchange exchange) {
            final Host selectedHost = super.selectHost(exchange);
            if (selectedHost == null) return null;
            final String nodeId;
            try {
                nodeId = (String) hostNodeIdField.get(selectedHost);
            } catch (final IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            if (nodeId == null) {
                throw new IllegalStateException("Can't find nodeId");
            }
            exchange.putAttachment(NODE_ID_KEY, nodeId);
            return selectedHost;
        }

        @Override
        protected Host findStickyHost(final HttpServerExchange exchange) {
            final Cookie nodeIdCookie = exchange.getRequestCookies().get(nodeIdCookieName);
            if (nodeIdCookie == null) return null;
            final String nodeId = nodeIdCookie.getValue();
            if (nodeId == null) return null;
            Host candidate = getHost(nodeId);
            if (candidate != null && isConnectionPoolAvailable(candidate)) return candidate;
            return null;
        }

        private boolean isConnectionPoolAvailable(Host host) {
            ProxyConnectionPool connectionPool;
            try {
                connectionPool = (ProxyConnectionPool) hostConnectionPoolField.get(host);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            ProxyConnectionPool.AvailabilityType state = connectionPool.available();
            return state != ProxyConnectionPool.AvailabilityType.CLOSED && state != ProxyConnectionPool.AvailabilityType.PROBLEM;
        }

        private Host getHost(final String nodeId) {
            final Map<String, Host> routes;
            try {
                routes = (Map<String, Host>) routesFiled.get(this);
            } catch (final IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            return routes.get(nodeId);
        }
    }
}
