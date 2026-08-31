package io.quarkus.registry.app.maven.cache;

import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.quarkus.registry.app.repository.DbStateRepository;
import jakarta.annotation.PostConstruct;

@Component
public class MavenCacheState {

    private static final Logger log = LoggerFactory.getLogger(MavenCacheState.class);

    private final MavenCache cache;

    private final DbStateRepository dbStateRepository;

    private Instant lastUpdate;

    public MavenCacheState(MavenCache cache, DbStateRepository dbStateRepository) {
        this.cache = cache;
        this.dbStateRepository = dbStateRepository;
    }

    /**
     * The Quarkus version of this bean was eagerly started (@Startup) and initialized the state in @PostConstruct so that
     * {@link #getLastUpdate()} never returns null on the first request.
     */
    @PostConstruct
    void init() {
        try {
            checkDbState();
        } catch (RuntimeException e) {
            log.warn("Unable to read the initial database state", e);
        }
    }

    /**
     * Check cache state on every 10 seconds
     */
    @Scheduled(cron = "${quarkus.registry.cache.cron:*/10 * * * * *}")
    void checkDbState() {
        log.debug("Checking cache state {}", lastUpdate);
        Instant updatedAt = dbStateRepository.findUpdatedAt();
        if (lastUpdate == null || !Objects.equals(lastUpdate, updatedAt)) {
            log.debug("Cache changed -> {}", updatedAt);
            lastUpdate = updatedAt;
            cache.clear();
        }
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }
}
