package com.github.zurekp.slb;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class CommandLineApp {
    private static final String DEFAULT_PROPERTY_FILE = "slb.properties";

    public static void main(final String[] args) throws IOException {
        final Properties properties = loadProperties(args);
        final Configuration config = createConfiguration(properties);

        new SimpleLoadBalancer(config).start();
    }

    private static Properties loadProperties(final String[] cmdLineArgs) throws IOException {
        final Properties result = new Properties();

        final Path defaultConfig = Paths.get(DEFAULT_PROPERTY_FILE).toAbsolutePath();
        if (Files.exists(defaultConfig)) {
            populateProperties(result, defaultConfig);
        }

        for (final String fileName : cmdLineArgs) {
            populateProperties(result, Paths.get(fileName).toAbsolutePath());
        }

        return result;
    }

    private static Configuration createConfiguration(final Properties properties) {
        final Configuration result = new Configuration();

        result.setKeyStoreType(properties.getProperty("keystore.type", Configuration.DEFAULT_KEYSTORE_TYPE));
        result.setKeyStorePassword(properties.getProperty("keystore.password", Configuration.DEFAULT_KEYSTORE_PASSWORD));
        result.setKeyStoreLocation(properties.getProperty("keystore.location", Configuration.DEFAULT_KEYSTORE_LOCATION));
        result.setPort(Integer.parseInt(properties.getProperty("port", Integer.toString(Configuration.DEFAULT_PORT))));
        result.setSslPort(Integer.parseInt(properties.getProperty("port.ssl", Integer.toString(Configuration.DEFAULT_SSL_PORT))));
        result.setHost(properties.getProperty("host", Configuration.DEFAULT_HOST));
        result.setNodeCookieName(properties.getProperty("node.cookie.name", Configuration.DEFAULT_NODE_COOKIE_NAME));
        result.setShutdownPath(properties.getProperty("shutdown.path", Configuration.DEFAULT_SHUTDOWN_PATH));

        final String[] allCookies = properties.getProperty("sticky.session.cookie.names", "").split("\\s*[;,\\s]\\s*");
        for (final String cookieName : allCookies)
            if (!cookieName.isEmpty())
                result.addStickySessionCookieName(cookieName);

        for (final String configKey : properties.stringPropertyNames()) {
            if (configKey.startsWith("node.") && configKey.endsWith(".uri") && configKey.length() > "node..uri".length()) {
                final String nodeId = configKey.substring("node.".length(), configKey.length() - ".uri".length());
                final URI uri = URI.create(properties.getProperty(configKey));
                result.addNode(uri, nodeId);
            }
        }

        return result;
    }

    private static void populateProperties(final Properties properties, final Path pathToFile) throws IOException {
        try (InputStream is = Files.newInputStream(pathToFile)) {
            properties.load(is);
        }
    }
}
