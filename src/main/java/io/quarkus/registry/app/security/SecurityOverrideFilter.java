package io.quarkus.registry.app.security;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * This is a very basic token authentication that check for a TOKEN that is set
 * in the Header, and if it match the configured token, set the use as the Admin user
 *
 * @author Phillip Kruger (phillip.kruger@redhat.com)
 */
public class SecurityOverrideFilter extends OncePerRequestFilter {

    public static final String ADMIN_ROLE = "admin";

    private final Optional<String> appToken;

    public SecurityOverrideFilter(String appToken) {
        this.appToken = Optional.ofNullable(appToken);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = request.getHeader("TOKEN");
        if (appToken.isEmpty() || Objects.equals(appToken.orElse(null), token)) {
            var authentication = UsernamePasswordAuthenticationToken.authenticated("Admin", null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + ADMIN_ROLE)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } else {
            if (!"GET".equalsIgnoreCase(request.getMethod())
                    && !"HEAD".equalsIgnoreCase(request.getMethod())) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpStatus.FORBIDDEN.value());
                return;
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
