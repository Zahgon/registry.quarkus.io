package io.quarkus.registry.app.rest;

/**
 * A single constraint violation, rendered exactly like the Quarkus/Hibernate Validator REST
 * response payload.
 */
public record Violation(String field, String message) {
}
