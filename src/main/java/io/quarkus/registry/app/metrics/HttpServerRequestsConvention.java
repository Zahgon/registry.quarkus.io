package io.quarkus.registry.app.metrics;

import java.util.Set;

import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

/**
 * The {@code http_server_requests_seconds} labels Quarkus Micrometer recorded, so the existing dashboards
 * keep matching: {@code method}, {@code outcome}, {@code status} and {@code uri} (plus {@code op} from
 * {@link HistogramMeterFilter}). {@code uri} is the JAX-RS template ({@code /maven/{path:.+}} for the
 * catch-all Maven route), the request path for a static file, and {@code NOT_FOUND} for a request no
 * route or file answered, and the request path for a route asked with a method it does not declare.
 */
@Component
public class HttpServerRequestsConvention extends DefaultServerRequestObservationConvention {

    private static final String SPRING_CATCH_ALL = "/**";

    private static final Set<String> MAVEN_PATTERNS = Set.of("/maven/{*path}");

    @Override
    public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
        return KeyValues.of(method(context), outcome(context), status(context), uri(context));
    }

    @Override
    protected KeyValue uri(ServerRequestObservationContext context) {
        String pattern = context.getPathPattern();
        int status = context.getResponse() == null ? 0 : context.getResponse().getStatus();
        if (pattern != null && MAVEN_PATTERNS.contains(pattern)) {
            return KeyValue.of("uri", "/maven/{path:.+}");
        }
        if (pattern == null || SPRING_CATCH_ALL.equals(pattern)) {
            if (status == 405) {
                // the route exists, only its method does not: Quarkus named the route
                return KeyValue.of("uri", context.getCarrier().getRequestURI());
            }
            if (status == 404) {
                return KeyValue.of("uri", "NOT_FOUND");
            }
            if (pattern != null && status >= 200 && status < 400) {
                return KeyValue.of("uri", context.getCarrier().getRequestURI());
            }
        }
        return super.uri(context);
    }
}
