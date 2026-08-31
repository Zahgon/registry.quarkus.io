package io.quarkus.registry.app.maven;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.SnapshotVersion;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.registry.app.CacheNames;
import io.quarkus.registry.app.maven.cache.MavenCacheState;
import io.quarkus.registry.app.repository.PlatformReleaseRepository;

/**
 * Builds (and caches) the Maven metadata.
 *
 * This lives in a bean of its own because Spring's cache abstraction is proxy based: calling a {@code @Cacheable} method
 * from another method of the same bean would bypass the proxy and therefore the cache.
 */
@Component
public class MetadataFactory {

    private static final List<String> EMPTY_CLASSIFIER = Collections.singletonList("");

    private final MavenCacheState mavenCacheState;

    private final PlatformReleaseRepository platformReleaseRepository;

    public MetadataFactory(MavenCacheState mavenCacheState, PlatformReleaseRepository platformReleaseRepository) {
        this.mavenCacheState = mavenCacheState;
        this.platformReleaseRepository = platformReleaseRepository;
    }

    @Cacheable(cacheNames = CacheNames.METADATA, key = "'metadata-' + #artifact.toString()")
    public Metadata generateMetadata(ArtifactCoords artifact) {
        Metadata newMetadata = new Metadata();
        newMetadata.setGroupId(artifact.getGroupId());
        newMetadata.setArtifactId(artifact.getArtifactId());
        newMetadata.setVersion(artifact.getVersion());

        Versioning versioning = new Versioning();
        newMetadata.setVersioning(versioning);

        Instant lastUpdated = mavenCacheState.getLastUpdate();
        versioning.setLastUpdatedTimestamp(Date.from(lastUpdated));

        Snapshot snapshot = new Snapshot();
        versioning.setSnapshot(snapshot);
        snapshot.setTimestamp(versioning.getLastUpdated().substring(0, 8) + "." + versioning.getLastUpdated().substring(8));
        snapshot.setBuildNumber(1);

        final String baseVersion = artifact.getVersion().substring(0, artifact.getVersion().length() - "SNAPSHOT".length());
        addSnapshotVersion(versioning, snapshot, baseVersion, "pom", EMPTY_CLASSIFIER);
        addSnapshotVersion(versioning, snapshot, baseVersion, "json", EMPTY_CLASSIFIER);
        addSnapshotVersion(versioning, snapshot, baseVersion, "json", platformReleaseRepository.findQuarkusCores());
        addSnapshotVersion(versioning, snapshot, baseVersion, "json", List.of("all"));
        return newMetadata;
    }

    private static void addSnapshotVersion(Versioning versioning, Snapshot snapshot, final String baseVersion,
            String extension, List<String> classifiers) {
        final String version = baseVersion + snapshot.getTimestamp() + "-" + snapshot.getBuildNumber();
        for (String classifier : classifiers) {
            final SnapshotVersion sv = new SnapshotVersion();
            sv.setExtension(extension);
            sv.setVersion(version);
            sv.setClassifier(classifier);
            sv.setUpdated(versioning.getLastUpdated());
            versioning.addSnapshotVersion(sv);
        }
    }
}
