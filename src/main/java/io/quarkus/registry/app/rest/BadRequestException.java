package io.quarkus.registry.app.rest;

/**
 * Replaces {@code jakarta.ws.rs.BadRequestException}: signals an invalid client request and results
 * in a 400 response.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
