package io.quarkus.registry.app.maven;

import java.io.StringWriter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.registry.config.RegistriesConfigMapperHelper;
import io.quarkus.registry.config.RegistryConfig;

@Component
public class RegistryDescriptorContentProvider implements ArtifactContentProvider {

    private final MavenConfig mavenConfig;

    private final RegistryConfigFactory registryConfigFactory;

    public RegistryDescriptorContentProvider(MavenConfig mavenConfig, RegistryConfigFactory registryConfigFactory) {
        this.mavenConfig = mavenConfig;
        this.registryConfigFactory = registryConfigFactory;
    }

    @Override
    public boolean supports(ArtifactCoords artifact, MavenRequest request) {
        return mavenConfig.matchesRegistryDescriptor(artifact);
    }

    @Override
    public ResponseEntity<String> provide(ArtifactCoords artifact, MavenRequest request) throws Exception {
        StringWriter sw = new StringWriter();
        RegistryConfig registryConfig = registryConfigFactory.getRegistryConfig();
        RegistriesConfigMapperHelper.toJson(registryConfig, sw);
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
