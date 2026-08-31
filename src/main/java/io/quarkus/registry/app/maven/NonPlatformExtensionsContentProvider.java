package io.quarkus.registry.app.maven;

import java.io.StringWriter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.registry.app.DatabaseRegistryClient;
import io.quarkus.registry.catalog.CatalogMapperHelper;
import io.quarkus.registry.catalog.ExtensionCatalog;

@Component
public class NonPlatformExtensionsContentProvider implements ArtifactContentProvider {

    private final MavenConfig mavenConfig;

    private final ObjectMapper objectMapper;

    private final DatabaseRegistryClient registryClient;

    public NonPlatformExtensionsContentProvider(MavenConfig mavenConfig, ObjectMapper objectMapper,
            DatabaseRegistryClient registryClient) {
        this.mavenConfig = mavenConfig;
        this.objectMapper = objectMapper;
        this.registryClient = registryClient;
    }

    @Override
    public boolean supports(ArtifactCoords artifact, MavenRequest request) {
        return mavenConfig.matchesNonPlatformExtensions(artifact);
    }

    @Override
    public ResponseEntity<String> provide(ArtifactCoords artifact, MavenRequest request) throws Exception {
        String quarkusVersion = artifact.getClassifier();
        ExtensionCatalog catalog = registryClient.resolveNonPlatformExtensionsCatalog(quarkusVersion);

        StringWriter sw = new StringWriter();
        CatalogMapperHelper.serialize(objectMapper, catalog, sw);
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
