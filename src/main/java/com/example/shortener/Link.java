package com.example.shortener;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "links")
public class Link {
    @Id @Column(length = 32) private String code;
    @Column(name = "target_url", nullable = false, length = 2048) private String targetUrl;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(nullable = false) private long clicks;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "last_accessed_at") private Instant lastAccessedAt;
    protected Link() { }
    Link(String code, String targetUrl, Instant createdAt, Instant expiresAt) {
        this.code = code; this.targetUrl = targetUrl; this.createdAt = createdAt; this.expiresAt = expiresAt;
    }
    public String getCode() { return code; }
    public String getTargetUrl() { return targetUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isEnabled() { return enabled; }
    public long getClicks() { return clicks; }
    public Instant getLastAccessedAt() { return lastAccessedAt; }
}
