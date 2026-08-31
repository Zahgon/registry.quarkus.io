package io.quarkus.registry.app.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import io.quarkus.registry.app.security.SecurityOverrideFilter;

/**
 * Replaces the {@code quarkus.http.cors.*} / {@code quarkus.http.header.*} configuration and the
 * {@code quarkus-security} role based access control.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String CONTENT_SECURITY_POLICY = "default-src 'self' 'unsafe-inline' 'unsafe-eval' data: blob:; frame-ancestors 'none'; form-action 'self'; block-all-mixed-content";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${TOKEN:#{null}}") String appToken) throws Exception {
        SecurityOverrideFilter securityOverrideFilter = new SecurityOverrideFilter(appToken);
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .anonymous(anonymous -> anonymous.disable())
                .headers(headers -> headers
                        // The exact header values are managed below, so the defaults are replaced
                        .contentTypeOptions(contentTypeOptions -> contentTypeOptions.disable())
                        .frameOptions(frameOptions -> frameOptions.disable())
                        .httpStrictTransportSecurity(hsts -> hsts.disable())
                        .cacheControl(cacheControl -> cacheControl.disable())
                        .addHeaderWriter(new StaticHeadersWriter("X-Content-Type-Options", "nosniff"))
                        .addHeaderWriter(new StaticHeadersWriter("X-Frame-Options", "deny"))
                        .addHeaderWriter(new StaticHeadersWriter("Strict-Transport-Security",
                                "max-age=31536000; includeSubDomains"))
                        .addHeaderWriter(new StaticHeadersWriter("Content-Security-Policy", CONTENT_SECURITY_POLICY)))
                .addFilterBefore(securityOverrideFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/admin/**").hasRole(SecurityOverrideFilter.ADMIN_ROLE)
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)))
                .build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
