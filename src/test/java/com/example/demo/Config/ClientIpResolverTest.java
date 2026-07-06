package com.example.demo.Config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    @Test
    void shouldIgnoreForwardedHeadersWhenRemoteAddressIsNotTrusted() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.10");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.1");

        assertEquals("203.0.113.10", resolver.resolve(request));
    }

    @Test
    void shouldUseForwardedForWhenRemoteAddressIsTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.10");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "198.51.100.1, 10.0.0.10");

        assertEquals("198.51.100.1", resolver.resolve(request));
    }

    @Test
    void shouldSkipTrustedProxyChainFromRightToAvoidSpoofedForwardedFor() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/24");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "203.0.113.200, 198.51.100.1, 10.0.0.11");

        assertEquals("198.51.100.1", resolver.resolve(request));
    }

    @Test
    void shouldFallBackToRemoteAddressWhenForwardedValueIsInvalid() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.10");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "not-an-ip");
        request.addHeader("X-Real-IP", "also-not-an-ip");

        assertEquals("10.0.0.10", resolver.resolve(request));
    }
}
