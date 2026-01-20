package com.example.chess.server.security;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;

public final class Tls {
    private Tls() {}

    public static SSLServerSocket createServerSocket(int port) throws Exception {
        String ksPath = System.getProperty("chess.tls.keystore");
        String ksPass = System.getProperty("chess.tls.keystore.password");
        String tsPath = System.getProperty("chess.tls.truststore", ksPath);
        String tsPass = System.getProperty("chess.tls.truststore.password", ksPass);

        if (ksPath == null || ksPass == null) {
            throw new IllegalStateException("TLS enabled but chess.tls.keystore / chess.tls.keystore.password not provided.");
        }

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(ksPath)) {
            keyStore.load(fis, ksPass.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, ksPass.toCharArray());

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(tsPath)) {
            trustStore.load(fis, tsPass.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        SSLServerSocketFactory factory = ctx.getServerSocketFactory();
        SSLServerSocket ss = (SSLServerSocket) factory.createServerSocket(port);

        // Keep this conservative:
        ss.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
        return ss;
    }
}