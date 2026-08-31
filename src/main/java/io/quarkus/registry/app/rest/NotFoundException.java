package io.quarkus.registry.app.rest;

/**
 * Replaces {@code jakarta.ws.rs.NotFoundException}: signals that the requested resource does not
 * exist and results in a 404 response.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException() {
        super();
    }

    public NotFoundException(String message) {
        super(message);
    }
}
