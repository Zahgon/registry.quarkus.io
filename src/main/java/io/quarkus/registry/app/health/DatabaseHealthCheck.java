package io.quarkus.registry.app.health;

import java.sql.Connection;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

/**
 * The readiness check {@code quarkus-agroal} registered for the default datasource, with the same name
 * and data: {@code "<default>": "UP"}, or the reason the validation failed.
 */
@Component
public class DatabaseHealthCheck implements HealthCheck {

    private static final int VALIDATION_TIMEOUT_SECONDS = 3;

    private final DataSource dataSource;

    public DatabaseHealthCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String name() {
        return "Database connections health check";
    }

    @Override
    public Kind kind() {
        return Kind.READINESS;
    }

    @Override
    public Result call() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                return Result.up(Map.of("<default>", "UP"));
            }
            return Result.down(Map.of("<default>", "validation check failed for the default DataSource"));
        } catch (Exception e) {
            return Result.down(Map.of("<default>",
                    "Unable to execute the validation check for the default DataSource: " + e.getMessage()));
        }
    }
}
