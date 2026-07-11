package com.ca.attendance.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersFilterTest {
    @Test
    void addsBrowserSecurityHeadersToEveryResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SecurityHeadersFilter().doFilter(
                new MockHttpServletRequest("GET", "/"),
                response,
                new MockFilterChain()
        );

        assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'self'");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    }
}
