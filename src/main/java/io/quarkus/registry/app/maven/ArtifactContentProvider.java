package io.quarkus.registry.app.maven;

import org.springframework.http.ResponseEntity;

import io.quarkus.maven.dependency.ArtifactCoords;

public interface ArtifactContentProvider {
    boolean supports(ArtifactCoords artifact, MavenRequest request);

    ResponseEntity<String> provide(ArtifactCoords artifact, MavenRequest request) throws Exception;

}
