package io.quarkus.registry.app.metrics;

import java.util.Set;

import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;

/**
 * Requests Quarkus Micrometer never metered: its own management routes under {@code /q/} and the
 * {@code quarkus.micrometer.binder.http-server.ignore-patterns} paths ({@code /favicon.ico}, {@code /},
 * {@code /index.html}).
 */
@Component
public class IgnoredRequestsPredicate implements ObservationPredicate {

    private static final Set<String> IGNORED_PATHS = Set.of("/favicon.ico", "/", "/index.html");

    @Override
    public boolean test(String name, Observation.Context context) {
        if (context instanceof ServerRequestObservationContext request) {
            String path = request.getCarrier().getRequestURI();
            return !(path.startsWith("/q/") || path.equals("/q") || IGNORED_PATHS.contains(path));
        }
        return true;
    }
}
