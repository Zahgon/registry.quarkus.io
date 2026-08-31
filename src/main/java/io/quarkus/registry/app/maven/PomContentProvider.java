package io.quarkus.registry.app.maven;

import java.io.IOException;
import java.io.StringWriter;

import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.RepositoryPolicy;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import io.quarkus.maven.dependency.ArtifactCoords;

@Component
public class PomContentProvider implements ArtifactContentProvider {

    private static final MavenXpp3Writer POM_WRITER = new MavenXpp3Writer();

    private final MavenConfig mavenConfig;

    public PomContentProvider(MavenConfig mavenConfig) {
        this.mavenConfig = mavenConfig;
    }

    @Override
    public boolean supports(ArtifactCoords artifact, MavenRequest request) {
        return mavenConfig.supports(artifact) &&
                artifact.getType().equals("pom");
    }

    @Override
    public ResponseEntity<String> provide(ArtifactCoords artifact, MavenRequest request) throws Exception {
        String result = generatePom(artifact, request);
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

    private static String generatePom(ArtifactCoords artifact, MavenRequest request) throws IOException {
        Model model = new Model();
        model.setGroupId(artifact.getGroupId());
        model.setArtifactId(artifact.getArtifactId());
        model.setVersion(artifact.getVersion());
        model.setPackaging("pom");

        final Repository repo = new Repository();
        repo.setId("quarkiverse-registry");
        repo.setName("Quarkiverse Extension Registry");
        repo.setUrl(request.baseUri().toString() + "/maven");

        RepositoryPolicy policy = new RepositoryPolicy();
        policy.setEnabled(true);
        policy.setUpdatePolicy("always");
        repo.setSnapshots(policy);

        model.addRepository(repo);

        StringWriter writer = new StringWriter();
        POM_WRITER.write(writer, model);
        return writer.toString();
    }

}
