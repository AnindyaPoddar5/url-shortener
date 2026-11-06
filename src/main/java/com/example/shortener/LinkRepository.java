package com.example.shortener;

import java.time.Instant;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface LinkRepository extends JpaRepository<Link, String> {
    // One conditional SQL update avoids read-modify-write lost increments and expiry races.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Link l set l.clicks = l.clicks + 1, l.lastAccessedAt = :now " +
           "where l.code = :code and l.enabled = true and (l.expiresAt is null or l.expiresAt > :now)")
    int recordClick(@Param("code") String code, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Link l set l.enabled = false where l.code = :code")
    int disable(@Param("code") String code);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Link l set l.expiresAt = :expiry where l.code = :code")
    int changeExpiry(@Param("code") String code, @Param("expiry") Instant expiry);
}
