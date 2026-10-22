package io.quarkus.registry.app.security;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The CORS policy of {@code quarkus.http.cors.enabled=true} with no origin configured: only the
 * server's own origin is allowed. A request whose {@code Origin} is that origin is answered normally and
 * carries {@code Access-Control-Allow-Origin} and {@code Access-Control-Allow-Credentials: false}; its
 * preflight answers 200 allowing the requested method. A request from any other origin, preflight or
 * not, answers 403 with no body. Requests without an {@code Origin} are not CORS requests.
 */
public class SameOriginCorsFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!isSameOrigin(origin, request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return;
        }
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "false");
        String requestedMethod = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
        if (HttpMethod.OPTIONS.matches(request.getMethod()) && requestedMethod != null) {
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, requestedMethod);
            response.setStatus(HttpStatus.OK.value());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isSameOrigin(String origin, HttpServletRequest request) {
        try {
            URI uri = URI.create(origin);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return false;
            }
            return uri.getScheme().equalsIgnoreCase(request.getScheme())
                    && uri.getHost().toLowerCase(Locale.ROOT).equals(request.getServerName().toLowerCase(Locale.ROOT))
                    && port(uri.getPort(), uri.getScheme()) == port(request.getServerPort(), request.getScheme());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int port(int port, String scheme) {
        if (port > 0) {
            return port;
        }
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
