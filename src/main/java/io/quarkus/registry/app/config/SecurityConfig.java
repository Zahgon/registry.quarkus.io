package io.quarkus.registry.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

import io.quarkus.registry.app.security.SameOriginCorsFilter;
import io.quarkus.registry.app.security.SecurityOverrideFilter;
import io.quarkus.registry.app.web.UnknownMethodFilter;

/**
 * Replaces the {@code quarkus.http.cors.*} / {@code quarkus.http.header.*} configuration and the
 * {@code quarkus-security} role based access control.
 *
 * <p>The admin token has no default outside the {@code dev} and {@code test} profiles: an application
 * started without {@code TOKEN} refuses to start rather than leaving every admin write open.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String CONTENT_SECURITY_POLICY = "default-src 'self' 'unsafe-inline' 'unsafe-eval' data: blob:; frame-ancestors 'none'; form-action 'self'; block-all-mixed-content";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${TOKEN:#{null}}") String appToken) throws Exception {
        if (appToken == null || appToken.isBlank()) {
            throw new IllegalStateException("TOKEN is not set: configure the admin token through the TOKEN "
                    + "environment variable or property before starting the registry");
        }
        SecurityOverrideFilter securityOverrideFilter = new SecurityOverrideFilter(appToken);
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
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
                // After the header writer, so a refused cross-origin request still carries the security headers
                .addFilterAfter(new SameOriginCorsFilter(), HeaderWriterFilter.class)
                .addFilterBefore(securityOverrideFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new UnknownMethodFilter(), SecurityOverrideFilter.class)
                // Writes need the admin role. Reads are routed first, as with @RolesAllowed after JAX-RS
                // matching, so an admin path read answers 404 or 405 rather than 403.
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/admin/**").hasRole(SecurityOverrideFilter.ADMIN_ROLE)
                        .requestMatchers(HttpMethod.PUT, "/admin/**").hasRole(SecurityOverrideFilter.ADMIN_ROLE)
                        .requestMatchers(HttpMethod.PATCH, "/admin/**").hasRole(SecurityOverrideFilter.ADMIN_ROLE)
                        .requestMatchers(HttpMethod.DELETE, "/admin/**").hasRole(SecurityOverrideFilter.ADMIN_ROLE)
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)))
                .build();
    }

    /**
     * RequestLineFilter has already normalised the path; the firewall still rejects what Vert.x would not
     * have: only an encoded slash or semicolon (left for routing to answer 404) and methods no route
     * declares (answered 403 without the token and 405 with it) are let through.
     */
    @Bean
    public WebSecurityCustomizer registryFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        firewall.setAllowSemicolon(true);
        firewall.setUnsafeAllowAnyHttpMethod(true);
        return web -> web.httpFirewall(firewall);
    }
}
