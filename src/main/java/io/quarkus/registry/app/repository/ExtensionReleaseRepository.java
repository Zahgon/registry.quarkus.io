package io.quarkus.registry.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.quarkus.registry.app.model.Extension;
import io.quarkus.registry.app.model.ExtensionRelease;

public interface ExtensionReleaseRepository
        extends JpaRepository<ExtensionRelease, Long>, ExtensionReleaseRepositoryCustom {

    Optional<ExtensionRelease> findByExtensionAndVersion(Extension extension, String version);

    @Query("""
            select er from ExtensionRelease er
            where er.extension.groupId = ?1 and er.extension.artifactId = ?2 and er.version = ?3
            """)
    Optional<ExtensionRelease> findByGAV(String groupId, String artifactId, String version);

    List<ExtensionRelease> findByExtension(Extension extension);
}
