package io.quarkus.registry.app.rest;

import java.util.List;

/**
 * The body returned when bean validation fails, matching the payload previously produced by the
 * Quarkus Hibernate Validator REST integration.
 */
public record ViolationReport(String title, int status, List<Violation> violations) {

    public static ViolationReport of(List<Violation> violations) {
        return new ViolationReport("Constraint Violation", 400, violations);
    }
}
