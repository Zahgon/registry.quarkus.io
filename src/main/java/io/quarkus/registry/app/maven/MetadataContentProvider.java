package io.quarkus.registry.app.maven;

import java.io.IOException;
import java.io.StringWriter;

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import io.quarkus.maven.dependency.ArtifactCoords;

@Component
public class MetadataContentProvider implements ArtifactContentProvider {

    private static final MetadataXpp3Writer METADATA_WRITER = new MetadataXpp3Writer();

    private final MavenConfig mavenConfig;

    private final MetadataFactory metadataFactory;

    public MetadataContentProvider(MavenConfig mavenConfig, MetadataFactory metadataFactory) {
        this.mavenConfig = mavenConfig;
        this.metadataFactory = metadataFactory;
    }

    @Override
    public boolean supports(ArtifactCoords artifact, MavenRequest request) {
        return mavenConfig.supports(artifact)
                && artifact.getType() != null &&
                artifact.getType().startsWith(ArtifactParser.MAVEN_METADATA_XML);
    }

    @Override
    public ResponseEntity<String> provide(ArtifactCoords artifact, MavenRequest request) throws IOException {
        Metadata metadata = metadataFactory.generateMetadata(artifact);
        String result = writeMetadata(metadata);
        final String checksumSuffix = ArtifactParser.getChecksumSuffix(request.pathSegments(), artifact);
        String contentType = MediaType.APPLICATION_XML_VALUE;
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

    private String writeMetadata(Metadata metadata) throws IOException {
        StringWriter sw = new StringWriter();
        METADATA_WRITER.write(sw, metadata);
        return sw.toString();
    }
}
