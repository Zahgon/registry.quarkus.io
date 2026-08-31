package io.quarkus.registry.app.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.registry.app.model.Platform;
import io.quarkus.registry.app.model.PlatformRelease;
import io.quarkus.registry.app.model.PlatformStream;

public interface PlatformReleaseRepository extends JpaRepository<PlatformRelease, Long> {

    Optional<PlatformRelease> findByPlatformStreamAndVersion(PlatformStream platformStream, String version);

    @Query(name = "PlatformRelease.findAllCorePlatforms")
    List<PlatformRelease> findAllCorePlatforms();

    @Query(name = "PlatformRelease.findByPlatformKey")
    List<PlatformRelease> findAllByPlatformKey(String platformKey, String version);

    @Query(name = "PlatformRelease.findQuarkusCores")
    List<String> findQuarkusCores();

    @Query(name = "PlatformRelease.findLatestByQuarkusCoreVersion")
    List<PlatformRelease> findLatestByQuarkusCoreVersion(String quarkusCore);

    @Query(name = "PlatformRelease.findLatest")
    List<PlatformRelease> findLatest();

    @Query(name = "PlatformRelease.findPinned")
    List<PlatformRelease> findPinned();

    @Query(name = "PlatformRelease.findLatestPinnedStream")
    List<PlatformRelease> findLatestPinnedStream();

    @Query(name = "PlatformRelease.findByArtifactCoordinates")
    List<PlatformRelease> findByArtifactCoordinates(@Param("groupId") String groupId,
            @Param("artifactId") String artifactId, @Param("version") String version);

    @Query(name = "PlatformRelease.countArtifactCoordinates")
    long countArtifactCoordinates(@Param("groupId") String groupId, @Param("artifactId") String artifactId,
            @Param("version") String version);

    default Optional<PlatformRelease> findByPlatformKey(String platformKey, String version) {
        return findAllByPlatformKey(platformKey, version).stream().findFirst();
    }

    default String findLatestQuarkusCore() {
        return findQuarkusCores().stream().findFirst().orElse(null);
    }

    /**
     * The version of a platform catalog artifact is carried by the artifact classifier.
     */
    default boolean artifactCoordinatesExist(ArtifactCoords artifact) {
        return countArtifactCoordinates(artifact.getGroupId(), artifact.getArtifactId(),
                artifact.getClassifier()) == 1;
    }

    default Optional<PlatformRelease> findByArtifactCoordinates(ArtifactCoords artifact) {
        return findByArtifactCoordinates(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier())
                .stream().findFirst();
    }

    default List<PlatformRelease> findLatest(String quarkusCore) {
        boolean withQuarkusCore = quarkusCore != null && !quarkusCore.isBlank();
        List<PlatformRelease> releases = withQuarkusCore
                ? findLatestByQuarkusCoreVersion(quarkusCore)
                : findLatest();
        // Check if we can perform this directly in the SQL
        var platformMap = new LinkedHashMap<Platform, List<PlatformRelease>>();
        for (var item : releases) {
            var platformReleases = platformMap.computeIfAbsent(item.platformStream.platform,
                    k -> new ArrayList<>());
            // Add all versions until we find the latest stable one
            boolean hasStableVersions = platformReleases.stream()
                    .anyMatch(PlatformRelease::isVersionStable);
            if (!hasStableVersions) {
                platformReleases.add(item);
            }
        }
        // Add pinned platforms to the result
        if (!withQuarkusCore) {
            for (var item : findPinned()) {
                platformMap.computeIfAbsent(item.platformStream.platform, k -> new ArrayList<>()).add(item);
            }
            // Add latest platforms from pinned streams
            for (var item : findLatestPinnedStream()) {
                platformMap.computeIfAbsent(item.platformStream.platform, k -> new ArrayList<>()).add(item);
            }
        }
        return platformMap.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }
}
