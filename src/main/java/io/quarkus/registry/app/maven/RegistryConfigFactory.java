package io.quarkus.registry.app.maven;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.registry.Constants;
import io.quarkus.registry.app.CacheNames;
import io.quarkus.registry.config.RegistryConfig;
import io.quarkus.registry.config.RegistryDescriptorConfig;
import io.quarkus.registry.config.RegistryMavenConfig;
import io.quarkus.registry.config.RegistryMavenRepoConfig;
import io.quarkus.registry.config.RegistryNonPlatformExtensionsConfig;
import io.quarkus.registry.config.RegistryPlatformsConfig;
import io.quarkus.registry.config.RegistryQuarkusVersionsConfig;

/**
 * Builds (and caches) the registry descriptor.
 *
 * This lives in a bean of its own because Spring's cache abstraction is proxy based: calling a {@code @Cacheable} method
 * from another method of the same bean would bypass the proxy and therefore the cache.
 */
@Component
public class RegistryConfigFactory {

    private final MavenConfig mavenConfig;

    public RegistryConfigFactory(MavenConfig mavenConfig) {
        this.mavenConfig = mavenConfig;
    }

    @Cacheable(cacheNames = CacheNames.DESCRIPTOR, key = "'registry-config'")
    public RegistryConfig getRegistryConfig() {
        final RegistryConfig.Mutable qer = RegistryConfig.builder();
        qer.setId(mavenConfig.getRegistryId());

        final RegistryDescriptorConfig.Mutable descriptor = RegistryDescriptorConfig.builder();
        descriptor.setArtifact(
                ArtifactCoords.of(mavenConfig.getRegistryGroupId(),
                        Constants.DEFAULT_REGISTRY_DESCRIPTOR_ARTIFACT_ID, null,
                        Constants.JSON, Constants.DEFAULT_REGISTRY_ARTIFACT_VERSION));
        qer.setDescriptor(descriptor.build());

        final RegistryMavenConfig.Mutable registryMavenConfig = RegistryMavenConfig.builder();

        final RegistryPlatformsConfig.Mutable platformsConfig = RegistryPlatformsConfig.builder();
        platformsConfig.setArtifact(ArtifactCoords.of(mavenConfig.getRegistryGroupId(),
                Constants.DEFAULT_REGISTRY_PLATFORMS_CATALOG_ARTIFACT_ID, null, Constants.JSON,
                Constants.DEFAULT_REGISTRY_ARTIFACT_VERSION));
        mavenConfig.getExtensionCatalogIncluded().ifPresent(platformsConfig::setExtensionCatalogsIncluded);
        qer.setPlatforms(platformsConfig.build());

        if (mavenConfig.supportsNonPlatforms()) {
            final RegistryNonPlatformExtensionsConfig.Mutable nonPlatformExtensionsConfig = RegistryNonPlatformExtensionsConfig
                    .builder();
            nonPlatformExtensionsConfig.setArtifact(ArtifactCoords.of(mavenConfig.getRegistryGroupId(),
                    Constants.DEFAULT_REGISTRY_NON_PLATFORM_EXTENSIONS_CATALOG_ARTIFACT_ID, null, Constants.JSON,
                    Constants.DEFAULT_REGISTRY_ARTIFACT_VERSION));
            qer.setNonPlatformExtensions(nonPlatformExtensionsConfig.build());
        }
        if (mavenConfig.getQuarkusVersionsExpression().isPresent()) {
            final RegistryQuarkusVersionsConfig.Mutable quarkusVersionsConfig = RegistryQuarkusVersionsConfig.builder();
            quarkusVersionsConfig.setRecognizedVersionsExpression(mavenConfig.getQuarkusVersionsExpression().orElse(null));
            quarkusVersionsConfig.setExclusiveProvider(mavenConfig.getQuarkusVersionsExclusiveProvider().orElse(false));
            qer.setQuarkusVersions(quarkusVersionsConfig.build());
        }
        final RegistryMavenRepoConfig.Mutable mavenRepo = RegistryMavenRepoConfig.builder();
        mavenRepo.setId(mavenConfig.getRegistryId());
        mavenRepo.setUrl(mavenConfig.getRegistryUrl());
        registryMavenConfig.setRepository(mavenRepo.build());
        qer.setMaven(registryMavenConfig.build());
        return qer.build();
    }
}
