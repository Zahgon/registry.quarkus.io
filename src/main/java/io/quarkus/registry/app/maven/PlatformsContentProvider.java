package io.quarkus.registry.app.maven;

import java.io.StringWriter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.registry.app.DatabaseRegistryClient;
import io.quarkus.registry.catalog.CatalogMapperHelper;
import io.quarkus.registry.catalog.PlatformCatalog;

/**
 * Lists the available platforms and their recommended versions, indicating which platform is the recommended default for new
 * projects
 */
@Component
public class PlatformsContentProvider implements ArtifactContentProvider {

    private final MavenConfig mavenConfig;

    private final ObjectMapper objectMapper;

    private final DatabaseRegistryClient registryClient;

    public PlatformsContentProvider(MavenConfig mavenConfig, ObjectMapper objectMapper,
            DatabaseRegistryClient registryClient) {
        this.mavenConfig = mavenConfig;
        this.objectMapper = objectMapper;
        this.registryClient = registryClient;
    }

    @Override
    public boolean supports(ArtifactCoords artifact, MavenRequest request) {
        return mavenConfig.matchesQuarkusPlatforms(artifact);
    }

    @Override
    public ResponseEntity<String> provide(ArtifactCoords artifact, MavenRequest request) throws Exception {
        var quarkusVersion = artifact.getClassifier();
        final PlatformCatalog platformCatalog;
        if (quarkusVersion.equals("all")) {
            platformCatalog = registryClient.resolveAllPlatforms();
        } else {
            platformCatalog = registryClient.resolveCurrentPlatformsCatalog(quarkusVersion);
        }
        if (platformCatalog == null || platformCatalog.getPlatforms().isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .header("X-Reason", "No platforms found")
                    .build();
        }
        StringWriter sw = new StringWriter();
        CatalogMapperHelper.serialize(objectMapper, platformCatalog, sw);
        String result = sw.toString();
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
}
