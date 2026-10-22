package io.quarkus.registry.app.web;

import java.io.IOException;
import java.util.Set;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A method no route declares (TRACE, PROPFIND, an invented one) answers 405 with no body, as RESTEasy
 * answered it, instead of the servlet container's 501 or echo. It runs after the token check, so such a
 * request without the admin token is still refused with 403 first.
 */
public class UnknownMethodFilter extends OncePerRequestFilter {

    private static final Set<String> ROUTABLE = Set.of("GET", "HEAD", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!ROUTABLE.contains(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setContentLength(0);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
