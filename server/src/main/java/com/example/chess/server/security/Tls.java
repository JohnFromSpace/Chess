package com.example.chess.server.security;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Tls {
    private Tls() {}

    private static final String[] DEFAULT_PROTOCOLS = new String[]{"TLSv1.3", "TLSv1.2"};

    public static SSLServerSocket createServerSocket(int port) throws Exception {
        String ksPath = System.getProperty("chess.tls.keystore");
        String ksPass = System.getProperty("chess.tls.keystore.password");
        String ksType = System.getProperty("chess.tls.keystore.type", KeyStore.getDefaultType());
        String tsPath = System.getProperty("chess.tls.truststore", ksPath);
        String tsPass = System.getProperty("chess.tls.truststore.password", ksPass);
        String tsType = System.getProperty("chess.tls.truststore.type", KeyStore.getDefaultType());

        if (ksPath == null || ksPass == null) {
            throw new IllegalStateException("TLS enabled but chess.tls.keystore / chess.tls.keystore.password not provided.");
        }

        KeyStore keyStore = KeyStore.getInstance(ksType);
        try (FileInputStream fis = new FileInputStream(ksPath)) {
            keyStore.load(fis, ksPass.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, ksPass.toCharArray());

        KeyStore trustStore = KeyStore.getInstance(tsType);
        try (FileInputStream fis = new FileInputStream(tsPath)) {
            trustStore.load(fis, tsPass.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        SSLServerSocketFactory factory = ctx.getServerSocketFactory();
        SSLServerSocket ss = (SSLServerSocket) factory.createServerSocket(port);

        configureSocket(ss);
        return ss;
    }

    private static void configureSocket(SSLServerSocket ss) {
        String[] requestedProtocols = parseCsv(System.getProperty("chess.tls.protocols"));
        if (requestedProtocols == null || requestedProtocols.length == 0) {
            requestedProtocols = DEFAULT_PROTOCOLS;
        }
        String[] protocols = filterSupported(requestedProtocols, ss.getSupportedProtocols(), ss.getEnabledProtocols());
        ss.setEnabledProtocols(protocols);

        String[] requestedCiphers = parseCsv(System.getProperty("chess.tls.ciphers"));
        if (requestedCiphers != null && requestedCiphers.length > 0) {
            String[] ciphers = filterSupported(requestedCiphers, ss.getSupportedCipherSuites(), null);
            if (ciphers.length > 0) {
                ss.setEnabledCipherSuites(ciphers);
            }
        }

        SSLParameters params = ss.getSSLParameters();
        params.setUseCipherSuitesOrder(true);
        ss.setSSLParameters(params);
    }

    private static String[] parseCsv(String value) {
        if (value == null) return null;
        String[] parts = value.split(",");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            String p = part.trim();
            if (!p.isEmpty()) out.add(p);
        }
        return out.toArray(new String[0]);
    }

    private static String[] filterSupported(String[] requested, String[] supported, String[] fallback) {
        if (requested == null || requested.length == 0) return fallback != null ? fallback : new String[0];
        if (supported == null || supported.length == 0) return fallback != null ? fallback : new String[0];

        List<String> supportedList = Arrays.asList(supported);
        List<String> out = new ArrayList<>();
        for (String r : requested) {
            if (supportedList.contains(r)) out.add(r);
        }
        if (out.isEmpty()) return fallback != null ? fallback : new String[0];
        return out.toArray(new String[0]);
    }
}
