package io.quarkus.registry.app.maven.cache;

import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.quarkus.registry.app.repository.DbStateRepository;
import jakarta.annotation.PostConstruct;

/**
 * The data generation of the registry: {@code db_state.updated_at}, which every admin write moves in
 * its own transaction. Each instance holds its own Maven cache, so the generation is what keeps several
 * instances on one database consistent. A Maven request reads it ({@link #currentGeneration()}) and
 * never serves an entry rendered for an older one; the scheduled check also drops the cache within 10
 * seconds of a change made directly in the database, as the Quarkus version did.
 */
@Component
public class MavenCacheState {

    private static final Logger log = LoggerFactory.getLogger(MavenCacheState.class);

    private final MavenCache cache;

    private final DbStateRepository dbStateRepository;

    private volatile Instant lastUpdate;

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
        currentGeneration();
    }

    /**
     * Reads the generation from the database, emptying this instance's cache when it moved.
     */
    public synchronized Instant currentGeneration() {
        Instant updatedAt = dbStateRepository.findUpdatedAt();
        if (lastUpdate == null || !Objects.equals(lastUpdate, updatedAt)) {
            log.debug("Cache changed -> {}", updatedAt);
            lastUpdate = updatedAt;
            cache.clear();
        }
        return updatedAt;
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }
}
