package io.quarkus.registry.app.maven;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.registry.Constants;
import io.quarkus.registry.app.model.Platform;
import io.quarkus.registry.app.model.PlatformRelease;
import io.quarkus.registry.app.repository.PlatformReleaseRepository;
import io.quarkus.registry.app.rest.NotFoundException;
import io.quarkus.registry.catalog.CatalogMapperHelper;
import io.quarkus.registry.catalog.Extension;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.catalog.ExtensionOrigin;

@Component
public class PlatformCatalogContentProvider implements ArtifactContentProvider {

    private final MavenConfig mavenConfig;

    private final PlatformReleaseRepository platformReleaseRepository;

    public PlatformCatalogContentProvider(MavenConfig mavenConfig, PlatformReleaseRepository platformReleaseRepository) {
        this.mavenConfig = mavenConfig;
        this.platformReleaseRepository = platformReleaseRepository;
    }

    /**
     * @return true only if
     *         <ul>
     *         <li>System property/env quarkus.registry.platform.extension-catalog-included is <code>true</code></li>
     *         <li>The requested artifact version is 1.0-SNAPSHOT or matches the qualifier</li>
     *         <li>The requested groupId/artifactId/classifier matches an existing {@link PlatformRelease}</li>
     *         </ul>
     */
    @Override
    public boolean supports(ArtifactCoords artifact, MavenRequest request) {
        return mavenConfig.getExtensionCatalogIncluded().orElse(Boolean.FALSE) &&
                (Constants.DEFAULT_REGISTRY_ARTIFACT_VERSION.equals(artifact.getVersion())
                        || Objects.equals(artifact.getClassifier(), artifact.getVersion()))
                && platformReleaseRepository.artifactCoordinatesExist(artifact);
    }

    @Override
    public ResponseEntity<String> provide(ArtifactCoords artifact, MavenRequest request) throws Exception {
        String result = findExtensionCatalog(artifact);
        final String checksumSuffix = ArtifactParser.getChecksumSuffix(request.pathSegments(), artifact);
        String contentType = MediaType.APPLICATION_JSON_VALUE;
        if (ArtifactParser.SUFFIX_MD5.equals(checksumSuffix)) {
            result = HashUtil.md5(result);
            contentType = MediaType.TEXT_PLAIN_VALUE;
        } else if (ArtifactParser.SUFFIX_SHA1.equals(checksumSuffix)) {
            result = HashUtil.sha1(result);
            contentType = MediaType.TEXT_PLAIN_VALUE;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(result);
    }

    private String findExtensionCatalog(ArtifactCoords artifact) throws IOException {
        PlatformRelease platformRelease = platformReleaseRepository.findByArtifactCoordinates(artifact)
                .orElseThrow(() -> new NotFoundException("Platform release requested not found"));
        Platform platform = platformRelease.platformStream.platform;
        //TODO: This information is not stored in the DB
        String id = ArtifactCoords.of(platform.groupId, platform.artifactId, platformRelease.version, "json",
                platformRelease.version).toString();
        ExtensionCatalog expected = ExtensionCatalog.builder()
                .setId(id)
                .setBom(ArtifactCoords.fromString(platformRelease.bom))
                .setPlatform(true)
                .setQuarkusCoreVersion(platformRelease.quarkusCoreVersion)
                .setMetadata(platformRelease.metadata)
                .setUpstreamQuarkusCoreVersion(platformRelease.upstreamQuarkusCoreVersion)
                .setCategories(
                        platformRelease.categories.stream().map(prc -> io.quarkus.registry.catalog.Category.builder()
                                .setId(prc.category.categoryKey)
                                .setName(prc.category.name)
                                .setMetadata(prc.metadata)
                                .setDescription(prc.category.description)
                                .build()).toList())
                .setExtensions(
                        platformRelease.extensions.stream().map(pe -> Extension.builder()
                                .setName(pe.extensionRelease.extension.name)
                                .setDescription(pe.extensionRelease.extension.description)
                                .setGroupId(pe.extensionRelease.extension.groupId)
                                .setArtifactId(pe.extensionRelease.extension.artifactId)
                                .setVersion(pe.extensionRelease.version)
                                .setMetadata(pe.metadata)
                                //TODO: This information is not stored in the DB
                                .setOrigins(List.of(ExtensionOrigin.builder().setId(id).build()))
                                .build()).toList())
                .build();
        return toString(expected);
    }

    private String toString(ExtensionCatalog catalog) throws IOException {
        StringWriter sw = new StringWriter();
        CatalogMapperHelper.serialize(catalog, sw);
        return sw.toString();
    }

}
