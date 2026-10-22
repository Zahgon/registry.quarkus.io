package io.quarkus.registry.app.rest;

import java.lang.reflect.Executable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * Replaces the JAX-RS {@code ExceptionMapper} providers previously declared in the
 * {@code io.quarkus.registry.app.resteasy} package. Extending
 * {@link ResponseEntityExceptionHandler} keeps the standard Spring MVC status codes (406, 405, ...)
 * intact next to the application specific mappings below.
 *
 * <p>RESTEasy answered those framework-level failures (unknown route, unsupported method, media type
 * not acceptable or not supported, unreadable body or header) with the status alone and an empty body,
 * where Spring renders an RFC 9457 problem document. {@link #handleExceptionInternal} keeps the
 * status and headers and drops the body, so every such answer is the one the original gave. An
 * unexpected failure answers 500 with Quarkus's own production error document,
 * {@code {"details": "Error id <id>", "stack": ""}}, and the same id is logged with the cause.
 */
@RestControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RestExceptionHandler.class);

    /** Error ids are {@code <uuid>-<n>}, as Quarkus prints them: one uuid per process, one counter per error. */
    private static final String ERROR_ID_PREFIX = UUID.randomUUID().toString();

    private static final AtomicLong ERROR_COUNT = new AtomicLong();

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleIllegalState(IllegalStateException exception) {
        return ResponseEntity.badRequest()
                .header("X-Reason", exception.getMessage())
                .build();
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> handleNotFound(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Object> handleBadRequest(BadRequestException exception) {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException exception) {
        List<Violation> violations = exception.getConstraintViolations().stream()
                .map(violation -> new Violation(fieldOf(violation), violation.getMessage()))
                .toList();
        return violationResponse(violations);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleInternalServerError(Exception exception) {
        String errorId = ERROR_ID_PREFIX + "-" + ERROR_COUNT.incrementAndGet();
        LOG.error("HTTP Request failed, error id: {}", errorId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("details", "Error id " + errorId, "stack", ""));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        return ResponseEntity.status(statusCode).headers(headers).build();
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Violation> violations = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new Violation(parameterNameOf(result), error.getDefaultMessage())))
                .toList();
        return violationResponse(violations);
    }

    private static ResponseEntity<Object> violationResponse(List<Violation> violations) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ViolationReport.of(violations));
    }

    private static String fieldOf(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() == null ? null : violation.getPropertyPath().toString();
    }

    /**
     * The name JAX-RS put in {@code violations[].field}, which is the propertyPath of the
     * constraint: {@code <method>.<parameter>}, not the bare parameter name.
     *
     * <p>Spring reports only the parameter, so this endpoint answered
     * {@code "field": "quarkusVersion"} where the original answered
     * {@code "field": "resolveNonPlatformExtensionsCatalog.quarkusVersion"}. The 400 document is
     * part of the published contract - a client reading that field sees a different value - so the
     * method name is put back.
     */
    private static String parameterNameOf(ParameterValidationResult result) {
        String name = result.getMethodParameter().getParameterName();
        if (name == null) {
            name = String.valueOf(result.getMethodParameter().getParameterIndex());
        }
        Executable executable = result.getMethodParameter().getExecutable();
        return executable == null ? name : executable.getName() + "." + name;
    }
}
