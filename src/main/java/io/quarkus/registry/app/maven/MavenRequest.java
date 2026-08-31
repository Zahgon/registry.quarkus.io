package io.quarkus.registry.app.maven;

import java.net.URI;
import java.util.List;

/**
 * Carries the information the content providers need from the incoming HTTP request.
 *
 * Replaces the JAX-RS {@code UriInfo} which has no direct Spring MVC equivalent.
 *
 * @param pathSegments the request path segments below {@code /maven}
 * @param baseUri the base URI of the application (scheme, host, port and context path)
 */
public record MavenRequest(List<String> pathSegments, URI baseUri) {

    public String path() {
        return "/maven/" + String.join("/", pathSegments);
    }
}
