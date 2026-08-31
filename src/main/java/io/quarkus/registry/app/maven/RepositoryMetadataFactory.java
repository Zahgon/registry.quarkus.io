package io.quarkus.registry.app.maven;

import java.io.IOException;
import java.io.StringWriter;

import org.sonatype.nexus.repository.metadata.model.RepositoryMetadata;
import org.sonatype.nexus.repository.metadata.model.io.xpp3.RepositoryMetadataXpp3Writer;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import io.quarkus.registry.app.CacheNames;

/**
 * Builds (and caches) the Nexus repository metadata document.
 *
 * This lives in a bean of its own because Spring's cache abstraction is proxy based: calling a {@code @Cacheable} method
 * from another method of the same bean would bypass the proxy and therefore the cache.
 */
@Component
public class RepositoryMetadataFactory {

    private final MavenConfig mavenConfig;

    public RepositoryMetadataFactory(MavenConfig mavenConfig) {
        this.mavenConfig = mavenConfig;
    }

    @Cacheable(cacheNames = CacheNames.METADATA, key = "'repository-metadata'")
    public String getRepositoryMetadata() throws IOException {
        RepositoryMetadata repositoryMetadata = new RepositoryMetadata();
        repositoryMetadata.setVersion(RepositoryMetadata.MODEL_VERSION);
        repositoryMetadata.setId(mavenConfig.getRegistryId());
        repositoryMetadata.setUrl(mavenConfig.getRegistryUrl());
        repositoryMetadata.setLayout(RepositoryMetadata.LAYOUT_MAVEN2);
        repositoryMetadata.setPolicy(RepositoryMetadata.POLICY_SNAPSHOT);
        return writeMetadata(repositoryMetadata);
    }

    private String writeMetadata(RepositoryMetadata metadata) throws IOException {
        StringWriter sw = new StringWriter();
        new RepositoryMetadataXpp3Writer().write(sw, metadata);
        return sw.toString();
    }
}
