package com.sense.prolt_operations.security;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    private static volatile List<String> trustedProxyRanges = Arrays.asList(
            "127.0.0.1",
            "0:0:0:0:0:0:0:1",
            "::1",
            "10.",
            "172.16.", "172.17.", "172.18.", "172.19.", "172.20.", "172.21.", "172.22.", "172.23.",
            "172.24.", "172.25.", "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31.",
            "192.168.",
            "169.254."
    );

    @Value("${app.security.trusted-proxies:}")
    private String configuredTrustedProxies;

    @PostConstruct
    public void init() {
        if (configuredTrustedProxies != null && !configuredTrustedProxies.isBlank()) {
            String[] parts = configuredTrustedProxies.split(",");
            List<String> list = new ArrayList<>();
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    list.add(trimmed);
                }
            }
            if (!list.isEmpty()) {
                trustedProxyRanges = list;
                log.info("ClientIpResolver initialized with {} custom trusted proxy patterns", list.size());
                return;
            }
        }
        log.info("ClientIpResolver initialized with default loopback and RFC 1918 trusted proxy ranges");
    }

    public static void setTrustedProxyRanges(List<String> ranges) {
        trustedProxyRanges = ranges != null ? ranges : List.of();
    }

    public static boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String cleanIp = ip.trim();
        for (String pattern : trustedProxyRanges) {
            if (cleanIp.equals(pattern) || cleanIp.startsWith(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves client IP address safely:
     * - Only inspects X-Forwarded-For if request.getRemoteAddr() is a recognized trusted proxy.
     * - If connection is direct (untrusted remoteAddr), X-Forwarded-For is ignored to prevent spoofing.
     * - Takes the rightmost client IP reported by the immediate reverse proxy.
     */
    public static String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return "127.0.0.1";
        }

        // Direct connection from untrusted source: do NOT trust proxy headers
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr.trim();
        }

        // Trusted proxy: parse X-Forwarded-For from right to left
        Enumeration<String> headers = request.getHeaders("X-Forwarded-For");
        String lastIp = null;
        if (headers != null) {
            while (headers.hasMoreElements()) {
                String headerVal = headers.nextElement();
                if (headerVal != null && !headerVal.isBlank()) {
                    String[] parts = headerVal.split(",");
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) {
                            lastIp = trimmed;
                        }
                    }
                }
            }
        }

        if (lastIp != null) {
            return lastIp;
        }

        return remoteAddr.trim();
    }
}
