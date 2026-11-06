package com.example.shortener;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded single-instance limiter. A shared gateway limiter is required for multiple replicas. */
public class RequestGuard extends OncePerRequestFilter {
    private final byte[] apiKey;
    private final Clock clock;
    private final int limit;
    private long minute = -1;
    private int requests;
    public RequestGuard(String apiKey, int limit, Clock clock) {
        if (apiKey == null || apiKey.length() < 24) throw new IllegalArgumentException("SHORTENER_API_KEY must contain at least 24 characters");
        if (limit < 1) throw new IllegalArgumentException("REQUESTS_PER_MINUTE must be positive");
        this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8); this.limit = limit; this.clock = clock;
    }
    private synchronized boolean allow() {
        long current = Math.floorDiv(clock.millis(), 60000);
        if (current != minute) { minute = current; requests = 0; }
        return ++requests <= limit;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/") || path.startsWith("/s/")) {
            if (!allow()) { response.setHeader("Retry-After", "60"); reject(response, 429, "Too Many Requests"); return; }
            if (request.getContentLengthLong() > 16384) { reject(response, 413, "Payload Too Large"); return; }
            // Also bound streamed/chunked bodies; Content-Length alone is insufficient.
            if (path.startsWith("/api/")) {
                String supplied = request.getHeader("X-API-Key");
                if (supplied == null || !MessageDigest.isEqual(apiKey, supplied.getBytes(StandardCharsets.UTF_8))) {
                    reject(response, 401, "Unauthorized"); return;
                }
                byte[] body = request.getInputStream().readNBytes(16385);
                if (body.length > 16384) { reject(response, 413, "Payload Too Large"); return; }
                HttpServletRequestWrapper wrapped = new HttpServletRequestWrapper(request) {
                    @Override public ServletInputStream getInputStream() {
                        var stream = new java.io.ByteArrayInputStream(body);
                        return new ServletInputStream() {
                            public int read() { return stream.read(); }
                            public boolean isFinished() { return stream.available() == 0; }
                            public boolean isReady() { return true; }
                            public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                        };
                    }
                };
                chain.doFilter(wrapped, response); return;
            }
        }
        chain.doFilter(request, response);
    }
    private void reject(HttpServletResponse response, int status, String title) throws IOException {
        response.setStatus(status); response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"type\":\"about:blank\",\"status\":" + status + ",\"title\":\"" + title + "\"}");
    }
}
