package com.github.zurekp.slb;

import java.net.URI;
import java.util.*;

import static java.util.Objects.requireNonNull;

public class Configuration {
    public static final int DEFAULT_PORT = 8080;
    public static final String DEFAULT_HOST = "localhost";
    public static final String DEFAULT_NODE_COOKIE_NAME = "slb_node_id";
    public static final String DEFAULT_SHUTDOWN_PATH = "slb/shutdown";

    private final Set<String> stickySessionCookieNames;
    private final Map<String, URI> nodeIdToNodeURI;
    private String host = DEFAULT_HOST;
    private int port = DEFAULT_PORT;
    private String nodeCookieName = DEFAULT_NODE_COOKIE_NAME;
    private String shutdownPath = DEFAULT_SHUTDOWN_PATH;

    public Configuration() {
        nodeIdToNodeURI = new HashMap<>();
        stickySessionCookieNames = new HashSet<>();
    }

    public String getHost() {
        return host;
    }

    public void setHost(final String host) {
        requireNonNull(host, "host can't be null");
        if (normalize(host).isEmpty()) throw new IllegalArgumentException("host can't be empty");

        this.host = normalize(host);
    }

    public int getPort() {
        return port;
    }

    public void setPort(final int port) {
        if (port <= 0) throw new IllegalArgumentException("port must be positive");

        this.port = port;
    }

    public String getNodeIdCookieName() {
        return nodeCookieName;
    }

    public void setNodeCookieName(final String nodeCookieName) {
        requireNonNull(nodeCookieName, "nodeCookieName can't be null");
        if (normalize(nodeCookieName).isEmpty()) throw new IllegalArgumentException("nodeCookieName can't be empty");

        this.nodeCookieName = normalize(nodeCookieName);
    }

    public void addStickySessionCookieName(final String cookieName) {
        requireNonNull(cookieName, "cookieName can't be null");
        if (normalize(cookieName).isEmpty()) throw new IllegalArgumentException("cookieName can't be empty");

        this.stickySessionCookieNames.add(cookieName.trim());
    }

    public Set<String> getStickySessionCookieNames() {
        return Collections.unmodifiableSet(stickySessionCookieNames);
    }

    public void addNode(final URI nodeURI, final String nodeId) {
        requireNonNull(nodeURI, "nodeURI can't be null");
        requireNonNull(nodeId, "nodeId can't be null");
        if (normalize(nodeId).isEmpty()) throw new IllegalArgumentException("nodeId can't be empty");

        nodeIdToNodeURI.put(normalize(nodeId), nodeURI);
    }

    public void setShutdownPath(String path) {
        requireNonNull(path, "path can't be null");
        if (normalize(path).isEmpty()) throw new IllegalArgumentException("path can't be empty");

        this.shutdownPath = path;
    }

    public String getShutdownPath() {
        return shutdownPath;
    }

    public Set<String> getNodeIds() {
        return Collections.unmodifiableSet(nodeIdToNodeURI.keySet());
    }

    public URI getNodeURI(final String nodeId) {
        requireNonNull(nodeId, "nodeId can't be null");
        if (normalize(nodeId).isEmpty()) throw new IllegalArgumentException("nodeId can't be empty");

        return nodeIdToNodeURI.get(normalize(nodeId));
    }

    private String normalize(final String s) {
        return s.trim().toLowerCase();
    }
}