package io.quarkus.registry.app.maven.cache;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A rendered Maven response, kept with the data generation it was rendered from.
 *
 * @param generation the {@code db_state.updated_at} that was current when rendering started
 * @param body the response bytes
 * @param headers the headers to replay (content type, ETag, Last-Modified, Cache-Control, ...)
 * @param pathPattern the route that rendered it, so a response served from the cache is metered under that route
 */
public record MavenResponse(Instant generation, byte[] body, Map<String, String> headers, String pathPattern) {

    public MavenResponse {
        headers = new LinkedHashMap<>(headers);
    }

    public String header(String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
