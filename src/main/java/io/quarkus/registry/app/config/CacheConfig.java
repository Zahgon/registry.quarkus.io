package io.quarkus.registry.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

import io.quarkus.registry.app.CacheNames;

/**
 * Replaces the Quarkus {@code quarkus-cache} extension: the named caches used by
 * {@code @Cacheable}/{@code @CacheEvict} are declared here.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(@Value("${maven.cache.maximum-size:10000}") int maximumSize) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(CacheNames.METADATA, CacheNames.DESCRIPTOR);
        cacheManager.setCaffeine(Caffeine.newBuilder().recordStats().maximumSize(maximumSize));
        return cacheManager;
    }
}
