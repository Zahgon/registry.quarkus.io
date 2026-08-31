package io.quarkus.registry.app.maven.cache;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.quarkus.registry.app.CacheNames;
import jakarta.annotation.PostConstruct;

/**
 * Holding the maven cache
 */
@Component
public class MavenCache {

    @Value("${maven.cache.maximum-size:10000}")
    int maximumSize;

    private Cache<String, MavenResponse> cache;

    private LocalDateTime lastInvalidateTime = LocalDateTime.MIN;

    @PostConstruct
    public void init() {
        this.cache = Caffeine.newBuilder()
                .recordStats()
                .maximumSize(maximumSize)
                .build();
    }

    @CacheEvict(cacheNames = CacheNames.METADATA, allEntries = true)
    public void clear() {
        cache.invalidateAll();
        lastInvalidateTime = LocalDateTime.now();
    }

    public LocalDateTime getLastInvalidateTime() {
        return this.lastInvalidateTime;
    }

    public Cache<String, MavenResponse> getCache() {
        return this.cache;
    }
}
