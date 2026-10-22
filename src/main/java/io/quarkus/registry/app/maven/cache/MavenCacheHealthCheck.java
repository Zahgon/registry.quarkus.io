package io.quarkus.registry.app.maven.cache;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import io.quarkus.registry.app.health.HealthCheck;

/**
 * Check the cache. A liveness check, as the MicroProfile {@code @Liveness} annotation made it.
 */
@Component
public class MavenCacheHealthCheck implements HealthCheck {

    private final MavenCache mavenCache;

    public MavenCacheHealthCheck(MavenCache mavenCache) {
        this.mavenCache = mavenCache;
    }

    @Override
    public String name() {
        return "Maven cache health";
    }

    @Override
    public Kind kind() {
        return Kind.LIVENESS;
    }

    @Override
    public Result call() {
        Cache<?, ?> cache = mavenCache.getCache();
        CacheStats stats = cache.stats();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("last invalidate all time", mavenCache.getLastInvalidateTime().toString());
        data.put("estimated size", cache.estimatedSize());
        data.put("request count", stats.requestCount());
        data.put("hit count", stats.hitCount());
        data.put("miss count", stats.missCount());
        data.put("load count", stats.loadCount());
        data.put("eviction count", String.valueOf(stats.evictionCount()));
        data.put("hit rate", String.valueOf(stats.hitRate()));
        data.put("miss rate", String.valueOf(stats.missRate()));
        return Result.up(data);
    }
}
