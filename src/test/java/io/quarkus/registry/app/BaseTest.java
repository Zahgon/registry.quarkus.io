package io.quarkus.registry.app;

import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import io.restassured.RestAssured;

/**
 * Base class for the integration tests. Boots the whole application on a random port and re-creates
 * the schema before every test method.
 *
 * <p>The database comes from {@link PostgresTestContainer}, which starts a container only when the
 * environment has not already supplied one - the same way Quarkus Dev Services stood down for the
 * original suite. That is what makes this suite runnable in a harness with no Docker daemon, which
 * the original's was and this one previously was not.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseTest {

    @Autowired
    Flyway flyway;

    @Autowired
    jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.registerProperties(registry);
    }

    @BeforeEach
    void migrate() {
        RestAssured.port = port;
        flyway.migrate();
        // The Hibernate second-level cache lives in the application context and therefore survives
        // flyway.clean(); without this, cached Category/Platform rows leak into the next test.
        entityManagerFactory.unwrap(SessionFactory.class).getCache().evictAllRegions();
    }

    @AfterEach
    void clean() {
        flyway.clean();
    }

    /**
     * Runs the given work in a committed transaction. The original tests relied on
     * {@code @Transactional} on the {@code @BeforeEach} method, which under Quarkus commits. Spring's
     * test-scoped {@code @Transactional} would instead roll back, and the data would not be visible
     * to the HTTP calls (which run in their own transaction), hence the explicit template.
     */
    protected void inTransaction(Runnable work) {
        transactionTemplate.executeWithoutResult(status -> work.run());
    }
}
