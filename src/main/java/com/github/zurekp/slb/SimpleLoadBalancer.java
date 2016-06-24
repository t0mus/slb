package com.github.zurekp.slb;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.Headers;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;

import static java.util.Objects.requireNonNull;

public class SimpleLoadBalancer {
    private final Undertow server;
    private final GracefulShutdownHandler shutdownHandler;

    public SimpleLoadBalancer(final Configuration configuration) {
        requireNonNull(configuration);

        shutdownHandler = Handlers.gracefulShutdown(createHandler(configuration));
        SSLContext sslContext = null;
        try
        {
            sslContext = createSslContext(configuration);
        }
        catch (IOException ioexception)
        {
            System.err.println("unable to create ssl context due to: " + ioexception.getMessage());
        }
        Undertow.Builder builder = Undertow.builder();
        if (sslContext!=null)
        {
            builder.addHttpsListener(configuration.getSslPort(), configuration.getHost(), sslContext);
        }
        builder.addHttpListener(configuration.getPort(), configuration.getHost()).setHandler(shutdownHandler);
        server = builder.build();
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

    private SSLContext createSslContext(final Configuration configuration) throws IOException {
        KeyStore keyStore = loadKeyStore(configuration.getKeyStoreLocation(), configuration.getKeyStoreType(), configuration.getKeyStorePassword());

        KeyManager[] keyManagers = buildKeyManagers(keyStore, configuration.getKeyStorePassword().toCharArray());
        TrustManager[] trustManagers = buildTrustManagers();

        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
        }
        catch (NoSuchAlgorithmException | KeyManagementException exc) {
            throw new IOException("Unable to create and initialise the SSLContext", exc);
        }

        return sslContext;
    }

    private KeyStore loadKeyStore(final String location, String type, String storePassword)
        throws IOException {

        final InputStream stream = new URL("file:"+location).openStream();
        try {
            KeyStore loadedKeystore = KeyStore.getInstance(type);
            loadedKeystore.load(stream, storePassword.toCharArray());
            return loadedKeystore;
        }
        catch (KeyStoreException | NoSuchAlgorithmException | CertificateException exc) {
            throw new IOException(String.format("Unable to load KeyStore %s", location), exc);
        }
        finally {
            stream.close();
        }
    }

    private TrustManager[] buildTrustManagers() throws IOException {
        TrustManager[] trustManagers = null;
            try {
                TrustManagerFactory trustManagerFactory = TrustManagerFactory
                    .getInstance(KeyManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init((KeyStore)null);
                trustManagers = trustManagerFactory.getTrustManagers();
            }
            catch (NoSuchAlgorithmException | KeyStoreException exc) {
                throw new IOException("Unable to initialise TrustManager[]", exc);
            }
        return trustManagers;
    }

    private static KeyManager[] buildKeyManagers(final KeyStore keyStore, char[] storePassword)
        throws IOException {
        KeyManager[] keyManagers;
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory
                .getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, storePassword);
            keyManagers = keyManagerFactory.getKeyManagers();
        }
        catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException exc) {
            throw new IOException("Unable to initialise KeyManager[]", exc);
        }
        return keyManagers;
    }

}