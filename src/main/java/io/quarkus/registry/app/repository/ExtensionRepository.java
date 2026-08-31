package io.quarkus.registry.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.quarkus.registry.app.model.Extension;

public interface ExtensionRepository extends JpaRepository<Extension, Long> {

    Optional<Extension> findByGroupIdAndArtifactId(String groupId, String artifactId);

    default Optional<Extension> findByGA(String groupId, String artifactId) {
        return findByGroupIdAndArtifactId(groupId, artifactId);
    }
}
