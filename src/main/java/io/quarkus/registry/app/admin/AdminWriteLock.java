package io.quarkus.registry.app.admin;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

/**
 * Serialises admin writes across every instance that shares the database: a PostgreSQL transaction-level
 * advisory lock, released when the calling transaction ends. A write's existence checks and inserts
 * then run as one step, so concurrent duplicates end in exactly one success and conflicts for the rest,
 * and concurrent imports of one catalog cannot race on its unique keys.
 */
@Component
public class AdminWriteLock {

    /** An arbitrary application-wide key: "registry" admin writes. */
    private static final long LOCK_KEY = 0x5245_4749_5354_5259L;

    private final EntityManager entityManager;

    public AdminWriteLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
        entityManager.createNativeQuery("select cast(pg_advisory_xact_lock(:key) as text)")
                .setParameter("key", LOCK_KEY)
                .getSingleResult();
    }
}
