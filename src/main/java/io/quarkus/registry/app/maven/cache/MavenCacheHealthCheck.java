package io.quarkus.registry.app.maven.cache;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;

/**
 * Check the cache
 */
@Component("Maven cache health")
public class MavenCacheHealthCheck implements HealthIndicator {

    private final MavenCache mavenCache;

    public MavenCacheHealthCheck(MavenCache mavenCache) {
        this.mavenCache = mavenCache;
    }

    @Override
    public Health health() {
        Cache<?, ?> cache = mavenCache.getCache();
        return Health.up()
                .withDetail("last invalidate all time", mavenCache.getLastInvalidateTime().toString())
                .withDetail("estimated size", cache.estimatedSize())
                .withDetail("request count", cache.stats().requestCount())
                .withDetail("hit count", cache.stats().hitCount())
                .withDetail("miss count", cache.stats().missCount())
                .withDetail("load count", cache.stats().loadCount())
                .withDetail("eviction count", String.valueOf(cache.stats().evictionCount()))
                .withDetail("hit rate", String.valueOf(cache.stats().hitRate()))
                .withDetail("miss rate", String.valueOf(cache.stats().missRate()))
                .build();
    }

}
