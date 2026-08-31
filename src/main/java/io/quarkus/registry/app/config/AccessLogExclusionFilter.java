package io.quarkus.registry.app.config;

import java.io.IOException;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Flags requests that must be kept out of the access log. The Tomcat access log valve is configured with
 * {@code conditionUnless=skipAccessLog}, so any request carrying that attribute is not logged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessLogExclusionFilter extends OncePerRequestFilter {

    static final String SKIP_ACCESS_LOG = "skipAccessLog";

    private final Pattern excludePattern;

    public AccessLogExclusionFilter(@Value("${registry.access-log.exclude-pattern:}") String excludePattern) {
        this.excludePattern = excludePattern == null || excludePattern.isBlank() ? null : Pattern.compile(excludePattern);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (excludePattern != null) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (excludePattern.matcher(path).matches()) {
                request.setAttribute(SKIP_ACCESS_LOG, Boolean.TRUE);
            }
        }
        filterChain.doFilter(request, response);
    }
}
