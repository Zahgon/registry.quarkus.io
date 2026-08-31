package io.quarkus.registry.app.rest;

import java.lang.reflect.Executable;
import java.util.List;

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
 */
@RestControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RestExceptionHandler.class);

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
        LOG.error("Internal server error occurred", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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
