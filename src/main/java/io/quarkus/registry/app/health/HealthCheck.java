package io.quarkus.registry.app.health;

import java.util.Map;

/**
 * One MicroProfile Health style check: a name, the probe it belongs to, and an UP/DOWN answer with data.
 */
public interface HealthCheck {

    enum Kind {
        LIVENESS,
        READINESS
    }

    String name();

    Kind kind();

    Result call();

    record Result(boolean up, Map<String, Object> data) {

        public static Result up(Map<String, Object> data) {
            return new Result(true, data);
        }

        public static Result down(Map<String, Object> data) {
            return new Result(false, data);
        }
    }
}
