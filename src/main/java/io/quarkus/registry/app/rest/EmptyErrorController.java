package io.quarkus.registry.app.rest;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Errors raised outside Spring MVC (by the servlet container or a filter) reach {@code /error}. Spring
 * Boot's default controller renders a JSON document there; Vert.x answered such failures with the status
 * alone, so this controller keeps the status and sends no body.
 */
@RestController
@Hidden
public class EmptyErrorController implements ErrorController {

    @RequestMapping("${server.error.path:/error}")
    public ResponseEntity<Void> error(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus httpStatus = status instanceof Integer code ? HttpStatus.resolve(code) : null;
        return ResponseEntity.status(httpStatus == null ? HttpStatus.INTERNAL_SERVER_ERROR : httpStatus).build();
    }
}
