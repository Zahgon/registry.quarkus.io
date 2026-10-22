package io.quarkus.registry.app.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The catch-all mapping, asserted directly.
 *
 * <p>A mutation that made {@code handleInternalServerError} answer 200 instead of 500 survived the
 * whole suite: every test drives the application over HTTP, and none of them provokes an exception
 * no other handler catches, so nothing reached this method. The mapping is still part of the
 * contract - it is what a client sees when the database is unreachable - so it
 * is asserted here on the handler itself rather than through a request.
 */
class RestExceptionHandlerTest {

    private final RestExceptionHandler handler = new RestExceptionHandler();

    @Test
    void should_answer_500_for_an_exception_no_other_handler_claims() {
        ResponseEntity<Object> response =
                handler.handleInternalServerError(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void should_not_leak_the_exception_message_to_the_client() {
        ResponseEntity<Object> response =
                handler.handleInternalServerError(new IllegalStateException("connection string: secret"));

        // Quarkus's production error document: an error id to find in the log, never the cause
        assertThat(response.getBody()).isInstanceOfSatisfying(java.util.Map.class, body -> {
            assertThat((String) body.get("details")).startsWith("Error id ");
            assertThat(body.get("stack")).isEqualTo("");
            assertThat(body.toString()).doesNotContain("secret");
        });
    }
}
