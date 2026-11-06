package com.example.shortener;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class RequestGuardTest {
    @Test void limiterRejectsExcessAndResets() throws Exception {
        var clock = org.mockito.Mockito.mock(Clock.class);
        org.mockito.Mockito.when(clock.millis()).thenReturn(0L);
        var guard = new RequestGuard("test-key-with-at-least-24-chars", 1, clock);
        var request = new MockHttpServletRequest("GET", "/s/demo"); request.setServletPath("/s/demo");
        guard.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        var limited = new MockHttpServletResponse(); guard.doFilter(request, limited, new MockFilterChain());
        assertThat(limited.getStatus()).isEqualTo(429); assertThat(limited.getHeader("Retry-After")).isEqualTo("60");
        org.mockito.Mockito.when(clock.millis()).thenReturn(60000L);
        var reset = new MockHttpServletResponse(); guard.doFilter(request, reset, new MockFilterChain());
        assertThat(reset.getStatus()).isEqualTo(200);
    }
    @Test void streamedBodiesAreBoundedWithoutContentLength() throws Exception {
        var guard = new RequestGuard("test-key-with-at-least-24-chars", 10, Clock.systemUTC());
        var request = new MockHttpServletRequest("POST", "/api/v1/links") {
            @Override public long getContentLengthLong() { return -1; }
        };
        request.setServletPath("/api/v1/links"); request.addHeader("X-API-Key", "test-key-with-at-least-24-chars");
        request.setContent(new byte[16385]); var response = new MockHttpServletResponse();
        guard.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(413);
    }
    @Test void insecureConfigurationFailsFast() {
        assertThatThrownBy(() -> new RequestGuard("short", 10, Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RequestGuard("test-key-with-at-least-24-chars", 0, Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
    }
}
