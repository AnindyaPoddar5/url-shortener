package com.example.shortener;

import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LinkService {
    private final LinkRepository repository;
    private final LinkWriter writer;
    private final CodeGenerator codes;
    private final UrlPolicy policy;
    private final Clock clock;
    private final TransactionTemplate transaction;
    public LinkService(LinkRepository repository, LinkWriter writer, CodeGenerator codes, UrlPolicy policy,
                       Clock clock, PlatformTransactionManager manager) {
        this.repository = repository; this.writer = writer; this.codes = codes; this.policy = policy;
        this.clock = clock; this.transaction = new TransactionTemplate(manager);
    }
    public Link create(String target, String alias, Instant expiry) {
        String validated = policy.validate(target);
        if (validated.length() > 2048) throw new ApiException(HttpStatus.BAD_REQUEST, "Encoded URL exceeds 2048 characters");
        validateExpiry(expiry);
        if (alias != null && !alias.matches("[A-Za-z0-9_-]{4,32}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Alias must contain 4-32 letters, digits, underscores or hyphens");
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = alias == null ? codes.next() : alias;
            try {
                // persist(), rather than save()/merge(), guarantees an existing alias cannot be overwritten.
                return transaction.execute(status -> writer.insert(new Link(code, validated, clock.instant(), expiry)));
            } catch (DataIntegrityViolationException ex) {
                if (alias != null) throw new ApiException(HttpStatus.CONFLICT, "Alias already exists");
                // Retry in a fresh transaction: a failed constraint leaves the original transaction unusable.
            }
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to allocate a code; try again");
    }

    @Transactional
    public String resolve(String code, boolean count) {
        Instant now = clock.instant();
        if (count && repository.recordClick(code, now) == 1) return get(code).getTargetUrl();
        Link link = get(code);
        if (!link.isEnabled()) throw new ApiException(HttpStatus.GONE, "Link is disabled");
        if (link.getExpiresAt() != null && !link.getExpiresAt().isAfter(now))
            throw new ApiException(HttpStatus.GONE, "Link has expired");
        return link.getTargetUrl();
    }
    @Transactional(readOnly = true)
    public Link get(String code) {
        return repository.findById(code).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Link not found"));
    }
    @Transactional
    public Link updateExpiry(String code, Instant expiry) {
        validateExpiry(expiry);
        if (repository.changeExpiry(code, expiry) == 0) throw new ApiException(HttpStatus.NOT_FOUND, "Link not found");
        return get(code);
    }
    @Transactional
    public void disable(String code) {
        if (repository.disable(code) == 0) throw new ApiException(HttpStatus.NOT_FOUND, "Link not found");
    }
    private void validateExpiry(Instant expiry) {
        if (expiry != null && !expiry.isAfter(clock.instant()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "expiresAt must be in the future");
    }
}
