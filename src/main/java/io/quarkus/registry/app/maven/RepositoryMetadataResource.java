package io.quarkus.registry.app.maven;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;

/**
 * Exposes Maven resource metadata for registering as a Nexus repository proxy
 */
@RestController
@RequestMapping("/maven/.meta")
public class RepositoryMetadataResource {

    private final MavenConfig mavenConfig;

    private final RepositoryMetadataFactory repositoryMetadataFactory;

    public RepositoryMetadataResource(MavenConfig mavenConfig, RepositoryMetadataFactory repositoryMetadataFactory) {
        this.mavenConfig = mavenConfig;
        this.repositoryMetadataFactory = repositoryMetadataFactory;
    }

    @GetMapping(value = "/prefixes.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @Hidden
    public String handlePrefixesTxt() {
        return "## repository-prefixes/2.0" + System.lineSeparator()
                + "/" + mavenConfig.getRegistryGroupId().replace('.', '/');
    }

    @GetMapping("/repository-metadata.{extension}")
    @Hidden
    public ResponseEntity<String> handleRepositoryMetadataRequest(@PathVariable("extension") String extension)
            throws IOException {
        String content = repositoryMetadataFactory.getRepositoryMetadata();
        String contentType = MediaType.APPLICATION_XML_VALUE;
        if (extension.endsWith(ArtifactParser.SUFFIX_SHA1)) {
            content = HashUtil.sha1(content);
            contentType = MediaType.TEXT_PLAIN_VALUE;
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, contentType).body(content);
    }
}
