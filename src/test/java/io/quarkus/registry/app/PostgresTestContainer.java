package io.quarkus.registry.app;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL for the test suite, started only when nothing else has supplied one.
 *
 * <p>This replaces the Quarkus Dev Services database the original suite relied on, and it has to
 * replace its BEHAVIOUR as well as its effect. Dev Services stand down the moment
 * {@code quarkus.datasource.jdbc.url} is configured, so the original suite runs against an ordinary
 * external PostgreSQL wherever one is provided and starts a container only when one is not.
 *
 * <p>An earlier version of this class started the container from a static initializer and registered
 * its properties unconditionally, which made a Docker daemon reachable from inside the test process a
 * hard requirement of the suite in every environment. The baseline passed 59 of 60 tests in a
 * container-less harness while this suite errored 57 of 71, and measured coverage fell from 26.54%
 * to 5.51% — not because less was tested, but because almost nothing ran.
 *
 * <p>The container is therefore created lazily and only on the path that needs it.
 */
public final class PostgresTestContainer {

    /** Set when the environment already provides a datasource; then no container is started. */
    private static final String CONFIGURED_URL = configuredUrl();

    private static volatile PostgreSQLContainer<?> instance;

    private PostgresTestContainer() {
    }

    private static String configuredUrl() {
        String url = System.getProperty("spring.datasource.url");
        if (url == null || url.isBlank()) {
            url = System.getenv("SPRING_DATASOURCE_URL");
        }
        return url == null || url.isBlank() ? null : url;
    }

    /**
     * Point the context at a database. When one is already configured this does nothing, leaving
     * Spring to read the datasource from the environment as it normally would.
     */
    public static void registerProperties(DynamicPropertyRegistry registry) {
        if (CONFIGURED_URL != null) {
            return;
        }
        PostgreSQLContainer<?> container = container();
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }

    private static PostgreSQLContainer<?> container() {
        PostgreSQLContainer<?> local = instance;
        if (local == null) {
            synchronized (PostgresTestContainer.class) {
                local = instance;
                if (local == null) {
                    local = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                            .withDatabaseName("registrydb")
                            .withUsername("postgresql")
                            .withPassword("postgresql");
                    local.start();
                    instance = local;
                }
            }
        }
        return local;
    }
}
