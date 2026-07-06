package com.example.demo.Config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

@Component
public class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";

    private final List<String> trustedProxies;

    public ClientIpResolver(@Value("${app.security.trusted-proxies:}") String trustedProxies) {
        this.trustedProxies = parseTrustedProxies(trustedProxies);
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedFor = resolveForwardedFor(request.getHeader(X_FORWARDED_FOR));
        if (isIpLiteral(forwardedFor)) {
            return forwardedFor;
        }

        String realIp = trimToNull(request.getHeader(X_REAL_IP));
        if (isIpLiteral(realIp)) {
            return realIp;
        }

        return remoteAddress;
    }

    private List<String> parseTrustedProxies(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> entries = new ArrayList<>();
        for (String part : value.split(",")) {
            String entry = part.trim();
            if (!entry.isBlank()) {
                entries.add(entry);
            }
        }
        return List.copyOf(entries);
    }

    private boolean isTrustedProxy(String remoteAddress) {
        if (!isIpLiteral(remoteAddress)) {
            return false;
        }
        for (String trustedProxy : trustedProxies) {
            if (matchesTrustedProxy(remoteAddress, trustedProxy)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesTrustedProxy(String remoteAddress, String trustedProxy) {
        if (trustedProxy.contains("/")) {
            return matchesCidr(remoteAddress, trustedProxy);
        }
        return addressEquals(remoteAddress, trustedProxy);
    }

    private boolean addressEquals(String left, String right) {
        if (!isIpLiteral(left) || !isIpLiteral(right)) {
            return false;
        }
        try {
            return InetAddress.getByName(left).equals(InetAddress.getByName(right));
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private boolean matchesCidr(String remoteAddress, String cidr) {
        String[] parts = cidr.split("/", 2);
        if (parts.length != 2 || !isIpLiteral(parts[0])) {
            return false;
        }
        try {
            int prefixLength = Integer.parseInt(parts[1]);
            byte[] remoteBytes = InetAddress.getByName(remoteAddress).getAddress();
            byte[] networkBytes = InetAddress.getByName(parts[0]).getAddress();
            if (remoteBytes.length != networkBytes.length || prefixLength < 0 || prefixLength > remoteBytes.length * 8) {
                return false;
            }
            BigInteger remote = new BigInteger(1, remoteBytes);
            BigInteger network = new BigInteger(1, networkBytes);
            int totalBits = remoteBytes.length * 8;
            BigInteger mask = BigInteger.ONE.shiftLeft(totalBits).subtract(BigInteger.ONE)
                    .shiftRight(prefixLength)
                    .not()
                    .and(BigInteger.ONE.shiftLeft(totalBits).subtract(BigInteger.ONE));
            return remote.and(mask).equals(network.and(mask));
        } catch (NumberFormatException | UnknownHostException e) {
            return false;
        }
    }

    private String resolveForwardedFor(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return null;
        }
        String[] chain = forwardedFor.split(",");
        for (int i = chain.length - 1; i >= 0; i--) {
            String candidate = trimToNull(chain[i]);
            if (!isIpLiteral(candidate)) {
                continue;
            }
            if (!isTrustedProxy(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isIpLiteral(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String candidate = value.trim();
        if (!isIpv4Literal(candidate) && !candidate.contains(":")) {
            return false;
        }
        try {
            return InetAddress.getByName(candidate).getHostAddress() != null;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private boolean isIpv4Literal(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isBlank() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                return false;
            }
            int octet = Integer.parseInt(part);
            if (octet < 0 || octet > 255) {
                return false;
            }
        }
        return true;
    }
}
