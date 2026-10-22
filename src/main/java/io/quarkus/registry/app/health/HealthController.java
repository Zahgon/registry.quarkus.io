package io.quarkus.registry.app.health;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;

/**
 * The MicroProfile Health endpoints the Quarkus application exposed, in their wire format:
 * {@code {"status": "UP"|"DOWN", "checks": [{"name", "status", "data"}]}}, 200 when every check is
 * UP and 503 otherwise. {@code /q/health} runs every check, {@code live} the liveness checks,
 * {@code ready} the readiness checks; {@code started} and {@code well} have no check of their own.
 */
@RestController
@Hidden
public class HealthController {

    private static final MediaType HEALTH_JSON = new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    private final List<HealthCheck> checks;

    public HealthController(List<HealthCheck> checks) {
        // Liveness first, as SmallRye Health lists them
        this.checks = checks.stream()
                .sorted((a, b) -> a.kind().compareTo(b.kind()))
                .toList();
    }

    @GetMapping("/q/health")
    public ResponseEntity<Map<String, Object>> health() {
        return report(check -> true);
    }

    @GetMapping("/q/health/live")
    public ResponseEntity<Map<String, Object>> live() {
        return report(check -> check.kind() == HealthCheck.Kind.LIVENESS);
    }

    @GetMapping("/q/health/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        return report(check -> check.kind() == HealthCheck.Kind.READINESS);
    }

    @GetMapping({ "/q/health/started", "/q/health/well" })
    public ResponseEntity<Map<String, Object>> noChecks() {
        return report(check -> false);
    }

    private ResponseEntity<Map<String, Object>> report(Predicate<HealthCheck> selected) {
        boolean up = true;
        List<Map<String, Object>> results = new ArrayList<>();
        for (HealthCheck check : checks) {
            if (!selected.test(check)) {
                continue;
            }
            HealthCheck.Result result = check.call();
            up &= result.up();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", check.name());
            entry.put("status", result.up() ? "UP" : "DOWN");
            entry.put("data", result.data());
            results.add(entry);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", up ? "UP" : "DOWN");
        body.put("checks", results);
        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(HEALTH_JSON)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
