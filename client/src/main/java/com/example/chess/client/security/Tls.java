package com.example.chess.client.security;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Tls {
    private Tls() {}

    private static final String[] DEFAULT_PROTOCOLS = new String[]{"TLSv1.3", "TLSv1.2"};

    public static Socket createClientSocket(String host, int port) throws Exception {
        String tsPath = System.getProperty("chess.tls.truststore");
        String tsPass = System.getProperty("chess.tls.truststore.password");
        String tsType = System.getProperty("chess.tls.truststore.type", KeyStore.getDefaultType());
        boolean verifyHost = Boolean.parseBoolean(System.getProperty("chess.tls.hostnameVerification", "true"));

        if (tsPath == null || tsPass == null) {
            throw new IllegalStateException("TLS enabled but chess.tls.truststore / chess.tls.truststore.password not provided.");
        }

        KeyStore trustStore = KeyStore.getInstance(tsType);
        try (FileInputStream fis = new FileInputStream(tsPath)) {
            trustStore.load(fis, tsPass.toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);

        SSLSocketFactory factory = ctx.getSocketFactory();
        SSLSocket s = (SSLSocket) factory.createSocket(host, port);
        SSLParameters params = s.getSSLParameters();
        if (verifyHost) {
            params.setEndpointIdentificationAlgorithm("HTTPS");
        }

        String[] requestedProtocols = parseCsv(System.getProperty("chess.tls.protocols"));
        if (requestedProtocols == null || requestedProtocols.length == 0) {
            requestedProtocols = DEFAULT_PROTOCOLS;
        }
        String[] protocols = filterSupported(requestedProtocols, s.getSupportedProtocols(), s.getEnabledProtocols());
        params.setProtocols(protocols);

        String[] requestedCiphers = parseCsv(System.getProperty("chess.tls.ciphers"));
        if (requestedCiphers != null && requestedCiphers.length > 0) {
            String[] ciphers = filterSupported(requestedCiphers, s.getSupportedCipherSuites(), null);
            if (ciphers.length > 0) {
                params.setCipherSuites(ciphers);
            }
        }

        s.setSSLParameters(params);
        s.startHandshake();
        return s;
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
