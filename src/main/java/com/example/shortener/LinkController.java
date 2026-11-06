package com.example.shortener;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class LinkController {
    public record CreateLink(@NotBlank @Size(max = 2048) String url, @Size(max = 32) String customAlias, Instant expiresAt) { }
    public record ExpiryUpdate(Instant expiresAt) { }
    public record LinkView(String code, String shortUrl, String url, Instant createdAt, Instant expiresAt,
                           long clicks, Instant lastAccessedAt, boolean enabled) { }
    private final LinkService service;
    private final String baseUrl;
    public LinkController(LinkService service, @Value("${app.base-url}") String baseUrl) {
        this.service = service; this.baseUrl = baseUrl.replaceAll("/+$", "");
        URI uri = URI.create(this.baseUrl);
        if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
            throw new IllegalArgumentException("BASE_URL must be an HTTP(S) origin");
    }
    @PostMapping("/api/v1/links")
    public ResponseEntity<LinkView> create(@Valid @RequestBody CreateLink body) {
        Link link = service.create(body.url(), body.customAlias(), body.expiresAt());
        return ResponseEntity.created(URI.create("/api/v1/links/" + link.getCode())).body(view(link));
    }
    @GetMapping("/api/v1/links/{code}")
    public LinkView analytics(@PathVariable String code) { return view(service.get(code)); }
    @PatchMapping("/api/v1/links/{code}/expiration")
    public LinkView expiry(@PathVariable String code, @RequestBody ExpiryUpdate body) {
        return view(service.updateExpiry(code, body.expiresAt()));
    }
    @DeleteMapping("/api/v1/links/{code}")
    public ResponseEntity<Void> disable(@PathVariable String code) {
        service.disable(code); return ResponseEntity.noContent().build();
    }
    @RequestMapping(value = "/s/{code}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<Void> redirect(@PathVariable String code, jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(service.resolve(code, "GET".equals(request.getMethod()))))
                .cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer").build();
    }
    private LinkView view(Link link) {
        return new LinkView(link.getCode(), baseUrl + "/s/" + link.getCode(), link.getTargetUrl(),
                link.getCreatedAt(), link.getExpiresAt(), link.getClicks(), link.getLastAccessedAt(), link.isEnabled());
    }
}
