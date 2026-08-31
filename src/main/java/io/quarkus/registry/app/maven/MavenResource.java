package io.quarkus.registry.app.maven;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.registry.app.repository.PlatformReleaseRepository;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Exposes a Maven resource for our tooling
 */
@RestController
@RequestMapping("/maven")
@Tag(name = "Client", description = "Client related services")
public class MavenResource {

    private static final Logger log = LoggerFactory.getLogger(MavenResource.class);

    private final MavenConfig mavenConfig;

    private final PlatformReleaseRepository platformReleaseRepository;

    private final ArtifactContentProvider[] contentProviders;

    public MavenResource(MavenConfig mavenConfig,
            PlatformReleaseRepository platformReleaseRepository,
            PomContentProvider pomContentProvider,
            MetadataContentProvider metadataContentProvider,
            RegistryDescriptorContentProvider registryDescriptorContentProvider,
            PlatformsContentProvider platformsContentProvider,
            NonPlatformExtensionsContentProvider nonPlatformExtensionsContentProvider,
            PlatformCatalogContentProvider platformCatalogContentProvider) {
        this.mavenConfig = mavenConfig;
        this.platformReleaseRepository = platformReleaseRepository;
        this.contentProviders = new ArtifactContentProvider[] {
                pomContentProvider,
                metadataContentProvider,
                registryDescriptorContentProvider,
                platformsContentProvider,
                nonPlatformExtensionsContentProvider,
                platformCatalogContentProvider
        };
    }

    private ArtifactContentProvider[] getContentProviders() {
        return contentProviders;
    }

    @GetMapping(value = { "", "/" }, produces = MediaType.TEXT_HTML_VALUE)
    @Hidden
    public ResponseEntity<String> welcomePage() {
        return ResponseEntity.ok(String.format("""
                        <!DOCTYPE html>
                        <head>
                            <title>Quarkus Registry Maven Repository</title>
                        </head>
                        <h1>Welcome to the Quarkus Registry Maven Repository</h1>
                        This endpoint provides <a href="https://quarkus.io/guides/platform">Quarkus platform</a> and <a href="https://quarkus.io/guides/extension-metadata">extension metadata</a> in the form of Maven artifacts for <a href="https://quarkus.io/guides/extension-registry-user">Quarkus Registry clients</a>.
                        It provides the following artifacts:
                        <ul>
                            <li><a href="%1$s/%2$s/quarkus-platforms/1.0-SNAPSHOT/quarkus-platforms-1.0-SNAPSHOT.json">Platforms</a></li>
                            <li><a href="%1$s/%2$s/quarkus-non-platform-extensions/1.0-SNAPSHOT/quarkus-non-platform-extensions-1.0-SNAPSHOT-%3$s.json">Non-platform extensions</a></li>
                            <li><a href="%1$s/%2$s/quarkus-registry-descriptor/1.0-SNAPSHOT/quarkus-registry-descriptor-1.0-SNAPSHOT.json">Registry Descriptor</a></li>
                        </ul>

                        If you use a Nexus Repository Manager and are looking for a way to configure it to use this repository, please refer to the <a href="https://quarkus.io/guides/extension-registry-user#how-to-register-as-a-nexus-repository-proxy">How to register as a Nexus Repository proxy guide</a>.
                        """,mavenConfig.getRegistryUrl(),
                            mavenConfig.getRegistryGroupId().replace(".", "/"),
                            platformReleaseRepository.findLatestQuarkusCore()
                        )
                );
    }

    @GetMapping("/{*path}")
    @Hidden
    public ResponseEntity<String> handleArtifactRequest(@PathVariable("path") String path) {
        List<String> pathSegments = toPathSegments(path);
        URI baseUri = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUri();
        MavenRequest request = new MavenRequest(pathSegments, baseUri);
        ArtifactCoords artifactCoords;
        try {
            artifactCoords = ArtifactParser.parseCoords(pathSegments);
        } catch (IllegalArgumentException | StringIndexOutOfBoundsException e) {
            log.debug("Error while parsing coords from {}", request.path(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        for (ArtifactContentProvider contentProvider : getContentProviders()) {
            if (contentProvider.supports(artifactCoords, request)) {
                try {
                    return contentProvider.provide(artifactCoords, request);
                } catch (IllegalArgumentException | IllegalStateException wae) {
                    // These errors will be handled by the Exception handlers
                    throw wae;
                } catch (RuntimeException re) {
                    if (isHandledByExceptionHandlers(re)) {
                        throw re;
                    }
                    log.error("Error while providing content", re);
                    return ResponseEntity.internalServerError().build();
                } catch (Exception e) {
                    log.error("Error while providing content", e);
                    return ResponseEntity.internalServerError().build();
                }
            }
        }
        log.debug("Not found: {}", request.path());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * The equivalent of the JAX-RS {@code WebApplicationException} passthrough: our own REST exceptions are translated by
     * the {@code RestExceptionHandler} and must not be swallowed into a 500 here.
     */
    private static boolean isHandledByExceptionHandlers(RuntimeException e) {
        return e instanceof io.quarkus.registry.app.rest.NotFoundException
                || e instanceof io.quarkus.registry.app.rest.BadRequestException;
    }

    private static List<String> toPathSegments(String path) {
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        if (normalized.isEmpty()) {
            return List.of();
        }
        return List.of(normalized.split("/"));
    }
}
