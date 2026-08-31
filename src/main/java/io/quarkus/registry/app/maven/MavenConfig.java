package io.quarkus.registry.app.maven;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.registry.Constants;
import jakarta.annotation.PostConstruct;

@Component
public class MavenConfig {

    @Value("${quarkus.registry.id:" + Constants.DEFAULT_REGISTRY_ID + "}")
    String registryId;

    @Value("${quarkus.registry.maven-repo.url:" + Constants.DEFAULT_REGISTRY_MAVEN_REPO_URL + "}")
    String registryUrl;

    @Value("${quarkus.registry.groupId:" + Constants.DEFAULT_REGISTRY_GROUP_ID + "}")
    String registryGroupId;

    @Value("${quarkus.registry.non-platform-extensions.support:true}")
    boolean supportsNonPlatforms;

    @Value("${quarkus.registry.quarkus-versions.expression:#{null}}")
    String quarkusVersionsExpression;

    @Value("${quarkus.registry.quarkus-versions.exclusive-provider:#{null}}")
    Boolean quarkusVersionsExclusiveProvider;

    @Value("${quarkus.registry.platform.extension-catalog-included:#{null}}")
    Boolean extensionCatalogIncluded;

    private ArtifactCoords nonPlatformExtensionCoords;

    @PostConstruct
    void init() {
        this.nonPlatformExtensionCoords = ArtifactCoords.of(registryGroupId,
                Constants.DEFAULT_REGISTRY_NON_PLATFORM_EXTENSIONS_CATALOG_ARTIFACT_ID,
                null,
                Constants.JSON,
                Constants.DEFAULT_REGISTRY_ARTIFACT_VERSION);
    }

    public String getNonPlatformExtensionCoords(String quarkusVersion) {
        return ArtifactCoords.of(nonPlatformExtensionCoords.getGroupId(),
                nonPlatformExtensionCoords.getArtifactId(),
                quarkusVersion,
                nonPlatformExtensionCoords.getType(),
                nonPlatformExtensionCoords.getVersion()).toString();
    }

    public boolean supports(ArtifactCoords artifact) {
        return matchesQuarkusPlatforms(artifact) ||
                matchesRegistryDescriptor(artifact) ||
                matchesNonPlatformExtensions(artifact);
    }

    /**
     * io.quarkus.registry:quarkus-platforms::json:1.0-SNAPSHOT
     * io.quarkus.registry:quarkus-platforms:<QUARKUS-VERSION>:json:1.0-SNAPSHOT
     *
     * A JSON file that lists the preferred versions of every registered platform (e.g. quarkus-bom, quarkus-universe-bom, etc).
     * It also indicates which platform is the default one (for project creation), e.g. the quarkus-universe-bom;
     */
    public boolean matchesQuarkusPlatforms(ArtifactCoords artifact) {
        return registryGroupId.equals(artifact.getGroupId()) &&
                Constants.DEFAULT_REGISTRY_PLATFORMS_CATALOG_ARTIFACT_ID.equals(artifact.getArtifactId()) &&
                Constants.DEFAULT_REGISTRY_ARTIFACT_VERSION.equals(artifact.getVersion());
    }

    /**
     * io.quarkus.registry:quarkus-non-platform-extensions:<QUARKUS-VERSION>:json:1.0-SNAPSHOT -
     *
     * JSON catalog of non-platform extensions that are compatible with a given Quarkus core version expressed
     * with <QUARKUS-VERSION> as the artifact’s classifier;
     */
    public boolean matchesNonPlatformExtensions(ArtifactCoords artifact) {
        return registryGroupId.equals(artifact.getGroupId()) &&
                Constants.DEFAULT_REGISTRY_NON_PLATFORM_EXTENSIONS_CATALOG_ARTIFACT_ID.equals(artifact.getArtifactId()) &&
                Constants.DEFAULT_REGISTRY_ARTIFACT_VERSION.equals(artifact.getVersion());
    }

    /**
     * io.quarkus.registry:quarkus-registry-descriptor::json:1.0-SNAPSHOT -
     *
     * The JSON registry descriptor which includes the default settings to communicate with the registry
     * (including specific groupId, artifactId and versions for the QER artifacts described above, Maven repository URL, etc).
     */
    public boolean matchesRegistryDescriptor(ArtifactCoords artifact) {
        return registryGroupId.equals(artifact.getGroupId()) &&
                Constants.DEFAULT_REGISTRY_DESCRIPTOR_ARTIFACT_ID.equals(artifact.getArtifactId()) &&
                Constants.DEFAULT_REGISTRY_ARTIFACT_VERSION.equals(artifact.getVersion());
    }

    public String getRegistryId() {
        return registryId;
    }

    public String getRegistryGroupId() {
        return registryGroupId;
    }

    public String getRegistryUrl() {
        return registryUrl;
    }

    public boolean supportsNonPlatforms() {
        return supportsNonPlatforms;
    }

    public Optional<String> getQuarkusVersionsExpression() {
        return Optional.ofNullable(quarkusVersionsExpression);
    }

    public Optional<Boolean> getQuarkusVersionsExclusiveProvider() {
        return Optional.ofNullable(quarkusVersionsExclusiveProvider);
    }

    public Optional<Boolean> getExtensionCatalogIncluded() {
        return Optional.ofNullable(extensionCatalogIncluded);
    }

}
