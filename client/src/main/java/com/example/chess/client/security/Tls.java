package com.example.chess.client.security;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.net.Socket;
import java.security.KeyStore;

public final class Tls {
    private Tls() {}

    public static Socket createClientSocket(String host, int port) throws Exception {
        String tsPath = System.getProperty("chess.tls.truststore");
        String tsPass = System.getProperty("chess.tls.truststore.password");

        if (tsPath == null || tsPass == null) {
            throw new IllegalStateException("TLS enabled but chess.tls.truststore / chess.tls.truststore.password not provided.");
        }

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(tsPath)) {
            trustStore.load(fis, tsPass.toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);

        SSLSocketFactory factory = ctx.getSocketFactory();
        SSLSocket s = (SSLSocket) factory.createSocket(host, port);
        s.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
        s.startHandshake();
        return s;
    }
}
