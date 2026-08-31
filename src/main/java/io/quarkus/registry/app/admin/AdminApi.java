package io.quarkus.registry.app.admin;

import static org.apache.commons.lang3.StringUtils.abbreviate;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.registry.app.config.YAMLMediaTypes;
import io.quarkus.registry.app.events.ExtensionCatalogDeleteEvent;
import io.quarkus.registry.app.events.ExtensionCatalogImportEvent;
import io.quarkus.registry.app.events.ExtensionCompatibilityCreateEvent;
import io.quarkus.registry.app.events.ExtensionCompatibleDeleteEvent;
import io.quarkus.registry.app.events.ExtensionCreateEvent;
import io.quarkus.registry.app.events.ExtensionDeleteEvent;
import io.quarkus.registry.app.events.ExtensionReleaseDeleteEvent;
import io.quarkus.registry.app.maven.cache.MavenCache;
import io.quarkus.registry.app.model.Category;
import io.quarkus.registry.app.model.Extension;
import io.quarkus.registry.app.model.ExtensionRelease;
import io.quarkus.registry.app.model.Platform;
import io.quarkus.registry.app.model.PlatformExtension;
import io.quarkus.registry.app.model.PlatformRelease;
import io.quarkus.registry.app.model.PlatformReleaseCategory;
import io.quarkus.registry.app.model.PlatformStream;
import io.quarkus.registry.app.repository.CategoryRepository;
import io.quarkus.registry.app.repository.DbStateRepository;
import io.quarkus.registry.app.repository.ExtensionReleaseRepository;
import io.quarkus.registry.app.repository.ExtensionRepository;
import io.quarkus.registry.app.repository.PlatformExtensionRepository;
import io.quarkus.registry.app.repository.PlatformReleaseCategoryRepository;
import io.quarkus.registry.app.repository.PlatformReleaseRepository;
import io.quarkus.registry.app.repository.PlatformRepository;
import io.quarkus.registry.app.repository.PlatformStreamRepository;
import io.quarkus.registry.app.rest.BadRequestException;
import io.quarkus.registry.app.rest.NotFoundException;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/admin")
@Validated
@SecurityScheme(name = "Authentication", description = "Admin token", type = SecuritySchemeType.APIKEY, paramName = "TOKEN", in = SecuritySchemeIn.HEADER)
@Tag(name = "Admin", description = "Admin related services")
@SuppressWarnings("unchecked")
public class AdminApi {

    private static final Logger LOG = LoggerFactory.getLogger(AdminApi.class);

    /**
     * Maximum abbreviation width for extensions
     */
    private static final int MAX_ABBREVIATION_WIDTH = 100;

    private final AdminService adminService;

    private final MavenCache cache;

    private final ObjectMapper objectMapper;

    private final CategoryRepository categoryRepository;

    private final DbStateRepository dbStateRepository;

    private final ExtensionRepository extensionRepository;

    private final ExtensionReleaseRepository extensionReleaseRepository;

    private final PlatformRepository platformRepository;

    private final PlatformStreamRepository platformStreamRepository;

    private final PlatformReleaseRepository platformReleaseRepository;

    private final PlatformReleaseCategoryRepository platformReleaseCategoryRepository;

    private final PlatformExtensionRepository platformExtensionRepository;

    public AdminApi(AdminService adminService, MavenCache cache, ObjectMapper objectMapper,
            CategoryRepository categoryRepository, DbStateRepository dbStateRepository,
            ExtensionRepository extensionRepository, ExtensionReleaseRepository extensionReleaseRepository,
            PlatformRepository platformRepository, PlatformStreamRepository platformStreamRepository,
            PlatformReleaseRepository platformReleaseRepository,
            PlatformReleaseCategoryRepository platformReleaseCategoryRepository,
            PlatformExtensionRepository platformExtensionRepository) {
        this.adminService = adminService;
        this.cache = cache;
        this.objectMapper = objectMapper;
        this.categoryRepository = categoryRepository;
        this.dbStateRepository = dbStateRepository;
        this.extensionRepository = extensionRepository;
        this.extensionReleaseRepository = extensionReleaseRepository;
        this.platformRepository = platformRepository;
        this.platformStreamRepository = platformStreamRepository;
        this.platformReleaseRepository = platformReleaseRepository;
        this.platformReleaseCategoryRepository = platformReleaseCategoryRepository;
        this.platformExtensionRepository = platformExtensionRepository;
    }

    @PostMapping(value = "/v1/extension/catalog", produces = MediaType.APPLICATION_JSON_VALUE, consumes = {
            MediaType.APPLICATION_JSON_VALUE, YAMLMediaTypes.APPLICATION_JACKSON_YAML})
    @SecurityRequirement(name = "Authentication")
    @Operation(summary = "Inserts a new Extension Catalog (Platform Release) in the database", description = "Invoke this endpoint when a new extension catalog release is available")
    public ResponseEntity<Object> addExtensionCatalog(
            @NotNull(message = "X-Platform header missing") @RequestHeader(name = "X-Platform", required = false) String platformKey,
            @RequestHeader(name = "X-Platform-Type", required = false, defaultValue = "C") Platform.Type platformType,
            @RequestHeader(name = "X-Platform-Pinned", required = false, defaultValue = "false") boolean pinned,
            @NotNull(message = "Body payload is missing") @RequestBody(required = false) ExtensionCatalog catalog) {
        final ArtifactCoords bom = catalog.getBom();
        LOG.info("Adding catalog {}", abbreviate(String.valueOf(bom), MAX_ABBREVIATION_WIDTH));
        ExtensionCatalogImportEvent event = new ExtensionCatalogImportEvent(catalog, platformKey, pinned, platformType);
        adminService.onExtensionCatalogImport(event);
        cache.clear();
        return ResponseEntity.accepted().body(bom);
    }

    @DeleteMapping(value = "/v1/extension/catalog", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @SecurityRequirement(name = "Authentication")
    @Operation(summary = "Deletes an extension catalog (platform) from the database")
    public ResponseEntity<Object> deleteExtensionCatalog(
            @NotNull(message = "platformKey is missing") @RequestParam(name = "platformKey", required = false) String platformKey,
            @NotNull(message = "version is missing") @RequestParam(name = "version", required = false) String version) {
        PlatformRelease platformRelease = platformReleaseRepository.findByPlatformKey(platformKey, version)
                .orElseThrow(() -> new NotFoundException("No Platform Release found"));
        ExtensionCatalogDeleteEvent event = new ExtensionCatalogDeleteEvent(platformRelease);
        adminService.onExtensionCatalogDelete(event);
        cache.clear();
        return ResponseEntity.accepted().build();
    }

    @PostMapping(value = "/v1/extension", produces = MediaType.APPLICATION_JSON_VALUE, consumes = {
            MediaType.APPLICATION_JSON_VALUE, YAMLMediaTypes.APPLICATION_JACKSON_YAML})
    @SecurityRequirement(name = "Authentication")
    @Operation(summary = "Inserts an extension in the database")
    public ResponseEntity<Object> addExtension(
            @NotNull(message = "Body payload is missing") @RequestBody(required = false) io.quarkus.registry.catalog.Extension extension) {
        ArtifactCoords bom = extension.getArtifact();
        Optional<ExtensionRelease> extensionRelease = extensionReleaseRepository
                .findByGAV(bom.getGroupId(), bom.getArtifactId(), bom.getVersion());
        if (extensionRelease.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        LOG.info("Adding extension {}", abbreviate(String.valueOf(extension.getArtifact()), MAX_ABBREVIATION_WIDTH));
        ExtensionCreateEvent event = new ExtensionCreateEvent(extension);
        adminService.onExtensionCreate(event);
        cache.clear();
        return ResponseEntity.accepted().body(bom);
    }

    @DeleteMapping(value = "/v1/extension", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @SecurityRequirement(name = "Authentication")
    @Operation(summary = "Deletes an extension from the database")
    public ResponseEntity<Object> deleteExtension(
            @NotNull(message = "groupId is missing") @RequestParam(name = "groupId", required = false) String groupId,
            @NotNull(message = "artifactId is missing") @RequestParam(name = "artifactId", required = false) String artifactId,
            @RequestParam(name = "version", required = false) String version) {
        if (version != null) {
            ArtifactCoords entity = ArtifactCoords.of(groupId, artifactId, null, null, version);
            ExtensionRelease extensionRelease = extensionReleaseRepository.findByGAV(groupId, artifactId, version)
                    .orElseThrow(NotFoundException::new);
            // If an extension release belongs to a platform, do not remove
            if (!extensionRelease.platforms.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                        .body(entity);
            } else {
                LOG.info("Removing extension release {}:{}:s",
                        abbreviate(groupId, MAX_ABBREVIATION_WIDTH),
                        abbreviate(artifactId, MAX_ABBREVIATION_WIDTH),
                        abbreviate(version, MAX_ABBREVIATION_WIDTH));
                ExtensionReleaseDeleteEvent event = new ExtensionReleaseDeleteEvent(extensionRelease);
                adminService.onExtensionReleaseDelete(event);
                cache.clear();
                return ResponseEntity.accepted().body(entity);
            }
        } else {
            ArtifactKey entity = ArtifactKey.ga(groupId, artifactId);
            Extension extension = extensionRepository.findByGA(groupId, artifactId)
                    .orElseThrow(NotFoundException::new);
            LOG.info("Removing extension {}:{}", abbreviate(groupId, MAX_ABBREVIATION_WIDTH),
                    abbreviate(artifactId, MAX_ABBREVIATION_WIDTH));
            // If any extension release belongs to a platform, do not delete it
            if (extension.releases.stream().anyMatch(extensionRelease -> !extensionRelease.platforms.isEmpty())) {
                return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                        .body(entity);
            } else {
                ExtensionDeleteEvent event = new ExtensionDeleteEvent(extension);
                adminService.onExtensionDelete(event);
                cache.clear();
                return ResponseEntity.accepted().body(entity);
            }
        }
    }

    @PostMapping(value = "/v1/extension/compat", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @SecurityRequirement(name = "Authentication")
    @Operation(summary = "Flags an extension as compatible against a specific Quarkus version")
    public ResponseEntity<Object> addExtensionCompatibilty(
            @NotNull(message = "groupId is missing") @RequestParam(name = "groupId", required = false) String groupId,
            @NotNull(message = "artifactId is missing") @RequestParam(name = "artifactId", required = false) String artifactId,
            @NotNull(message = "version is missing") @RequestParam(name = "version", required = false) String version,
            @NotNull(message = "quarkusCore is missing") @RequestParam(name = "quarkusCore", required = false) String quarkusCore,
            @RequestParam(name = "compatible", required = false, defaultValue = "true") Boolean compatible) {
        ExtensionRelease extensionRelease = extensionReleaseRepository.findByGAV(groupId, artifactId, version)
                .orElseThrow(NotFoundException::new);
        LOG.info("Extension {}:{}:{} is {} with Quarkus {}",
                abbreviate(groupId, MAX_ABBREVIATION_WIDTH),
                abbreviate(artifactId, MAX_ABBREVIATION_WIDTH),
                abbreviate(version, MAX_ABBREVIATION_WIDTH),
                compatible ? "compatible" : "incompatible",
                abbreviate(quarkusCore, MAX_ABBREVIATION_WIDTH));
        ExtensionCompatibilityCreateEvent event = new ExtensionCompatibilityCreateEvent(extensionRelease, quarkusCore,
                compatible);
        adminService.onExtensionCompatibilityCreate(event);
        cache.clear();
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping(value = "/v1/extension/compat", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @SecurityRequirement(name = "Authentication")
    @Operation(summary = "Flags an extension as incompatible against a specific Quarkus version")
    public ResponseEntity<Object> removeExtensionCompatibilty(
            @NotNull(message = "groupId is missing") @RequestParam(name = "groupId", required = false) String groupId,
            @NotNull(message = "artifactId is missing") @RequestParam(name = "artifactId", required = false) String artifactId,
            @NotNull(message = "version is missing") @RequestParam(name = "version", required = false) String version,
            @NotNull(message = "quarkusCore is missing") @RequestParam(name = "quarkusCore", required = false) String quarkusCore) {
        ExtensionRelease extensionRelease = extensionReleaseRepository.findByGAV(groupId, artifactId, version)
                .orElseThrow(NotFoundException::new);
        LOG.info("Extension {}:{}:{} is no longer compatible with Quarkus {}",
                abbreviate(groupId, MAX_ABBREVIATION_WIDTH),
                abbreviate(artifactId, MAX_ABBREVIATION_WIDTH),
                abbreviate(version, MAX_ABBREVIATION_WIDTH),
                abbreviate(quarkusCore, MAX_ABBREVIATION_WIDTH));
        ExtensionCompatibleDeleteEvent event = new ExtensionCompatibleDeleteEvent(extensionRelease, quarkusCore);
        adminService.onExtensionCompatibilityDelete(event);
        cache.clear();
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/v1/maven/cache")
    @SecurityRequirement(name = "Authentication")
    @Operation(summary = "Clear the maven cache")
    public ResponseEntity<Object> clearCache() {
        cache.clear();
        return ResponseEntity.accepted().build();
    }

    @PatchMapping(value = "/v1/stream/{platformKey}/{streamKey}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @SecurityRequirement(name = "Authentication")
    @Transactional
    @Operation(summary = "Patches a PlatformStream")
    public ResponseEntity<Object> patchPlatformStream(
            @NotNull(message = "platformKey is missing") @PathVariable("platformKey") String platformKey,
            @NotNull(message = "streamKey is missing") @PathVariable("streamKey") String streamKey,
            @RequestParam(name = "unlisted", required = false, defaultValue = "false") boolean unlisted,
            @RequestParam(name = "pinned", required = false, defaultValue = "false") boolean pinned,
            @RequestParam(name = "lts", required = false, defaultValue = "false") boolean lts) {
        Platform platform = platformRepository.findByPlatformKey(platformKey)
                .orElseThrow(() -> new NotFoundException("Platform not found"));
        PlatformStream stream = platformStreamRepository.findByPlatformAndStreamKey(platform, streamKey)
                .orElseThrow(() -> new NotFoundException("Platform Stream not found"));
        stream.unlisted = unlisted;
        stream.pinned = pinned;
        stream.lts = lts;
        try {
            platformStreamRepository.saveAndFlush(stream);
            cache.clear();
        } finally {
            dbStateRepository.updateUpdatedAt();
        }
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/v1/stream/{platformKey}/{streamKey}")
    @SecurityRequirement(name = "Authentication")
    @Transactional
    @Operation(summary = "Deletes a PlatformStream")
    public ResponseEntity<Object> deletePlatformStream(
            @NotNull(message = "platformKey is missing") @PathVariable("platformKey") String platformKey,
            @NotNull(message = "streamKey is missing") @PathVariable("streamKey") String streamKey) {
        Platform platform = platformRepository.findByPlatformKey(platformKey)
                .orElseThrow(() -> new NotFoundException("Platform not found"));
        PlatformStream stream = platformStreamRepository.findByPlatformAndStreamKey(platform, streamKey)
                .orElseThrow(() -> new NotFoundException("Platform Stream not found"));
        try {
            // Delete the stream
            platformStreamRepository.delete(stream);
            cache.clear();
            // Delete the platform members too
            for (Platform member : platformRepository.findMembersByPlatform(platform)) {
                platformStreamRepository.findByPlatformAndStreamKey(member, streamKey)
                        .ifPresent(platformStreamRepository::delete);
                cache.clear();
            }
        } finally {
            dbStateRepository.updateUpdatedAt();
        }
        return ResponseEntity.accepted().build();
    }

    @PatchMapping(value = "/v1/platform-release/{platformKey}/{streamKey}/{version}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @SecurityRequirement(name = "Authentication")
    @Transactional
    @Operation(summary = "Patches a PlatformRelease", description = "Invoke this endpoint when maintaining a platform release")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "If invocation is successful"),
            @ApiResponse(responseCode = "404", description = "If Platform/Stream/Release were not found")
    })
    public ResponseEntity<Object> patchPlatformRelease(
            @NotNull(message = "platformKey is missing") @PathVariable("platformKey") String platformKey,
            @NotNull(message = "streamKey is missing") @PathVariable("streamKey") String streamKey,
            @NotNull(message = "version is missing") @PathVariable("version") String version,
            @RequestParam(name = "unlisted", required = false, defaultValue = "false") boolean unlisted,
            @RequestParam(name = "pinned", required = false, defaultValue = "false") boolean pinned,
            @RequestParam(name = "metadata", required = false) String metadataJson) {
        Map<String, Object> metadata = null;
        if (metadataJson != null && !metadataJson.isEmpty()) {
            try {
                metadata = objectMapper.readValue(metadataJson, Map.class);
            } catch (JsonProcessingException e) {
                throw new BadRequestException("Invalid metadata JSON", e);
            }
        }
        PlatformRelease platformRelease = findPlatformRelease(platformKey, streamKey, version);
        // Perform changes and persist
        platformRelease.unlisted = unlisted;
        platformRelease.pinned = pinned;
        if (metadata != null) {
            platformRelease.metadata = metadata;
        }
        try {
            platformReleaseRepository.saveAndFlush(platformRelease);
            cache.clear();
        } finally {
            dbStateRepository.updateUpdatedAt();
        }
        return ResponseEntity.accepted().build();
    }

    @PatchMapping(value = "/v1/platform-release/{platformKey}/{streamKey}/{version}/category/{categoryKey}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @SecurityRequirement(name = "Authentication")
    @Transactional
    @Operation(summary = "Patches a PlatformReleaseCategory", description = "Invoke this endpoint when the platform release needs to be set to unlisted")
    public ResponseEntity<Object> patchPlatformReleaseCategory(
            @NotNull(message = "platformKey is missing") @PathVariable("platformKey") String platformKey,
            @NotNull(message = "streamKey is missing") @PathVariable("streamKey") String streamKey,
            @NotNull(message = "version is missing") @PathVariable("version") String version,
            @NotNull(message = "categoryKey is missing") @PathVariable("categoryKey") String categoryKey,
            @RequestParam(name = "metadata", required = false) String metadataJson) {
        Map<String, Object> metadata;
        if (metadataJson != null && !metadataJson.isEmpty()) {
            try {
                metadata = objectMapper.readValue(metadataJson, Map.class);
            } catch (JsonProcessingException e) {
                throw new BadRequestException("Invalid metadata JSON", e);
            }
        } else {
            // Since metadata is the only parameter, return NO_CONTENT
            return ResponseEntity.noContent().build();
        }
        PlatformRelease platformRelease = findPlatformRelease(platformKey, streamKey, version);
        Category category = categoryRepository.findByCategoryKey(categoryKey)
                .orElseThrow(() -> new NotFoundException("Category not found"));
        PlatformReleaseCategory prc = platformReleaseCategoryRepository
                .findByPlatformReleaseAndCategory(platformRelease, category)
                .orElseThrow(() -> new NotFoundException("Platform Release Category not found"));
        // Perform changes and persist
        prc.metadata = metadata;
        try {
            platformReleaseCategoryRepository.saveAndFlush(prc);
            cache.clear();
        } finally {
            dbStateRepository.updateUpdatedAt();
        }
        return ResponseEntity.accepted().build();
    }

    @PatchMapping(value = "/v1/platform-release/{platformKey}/{streamKey}/{version}/extension/{extensionGroupId}/{extensionArtifactId}/{extensionVersion}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @SecurityRequirement(name = "Authentication")
    @Transactional
    @Operation(summary = "Patches a PlatformExtension", description = "Invoke this endpoint to patch a PlatformExtension")
    public ResponseEntity<Object> patchPlatformExtension(
            @NotNull(message = "platformKey is missing") @PathVariable("platformKey") String platformKey,
            @NotNull(message = "streamKey is missing") @PathVariable("streamKey") String streamKey,
            @NotNull(message = "version is missing") @PathVariable("version") String version,
            @NotNull(message = "extensionGroupId is missing") @PathVariable("extensionGroupId") String extensionGroupId,
            @NotNull(message = "extensionArtifactId is missing") @PathVariable("extensionArtifactId") String extensionArtifactId,
            @NotNull(message = "extensionVersion is missing") @PathVariable("extensionVersion") String extensionVersion,
            @RequestParam(name = "metadata", required = false) String metadataJson) {
        Map<String, Object> metadata;
        if (metadataJson != null && !metadataJson.isEmpty()) {
            try {
                metadata = objectMapper.readValue(metadataJson, Map.class);
            } catch (JsonProcessingException e) {
                throw new BadRequestException("Invalid metadata JSON", e);
            }
        } else {
            // Since metadata is the only parameter, return NO_CONTENT
            return ResponseEntity.noContent().build();
        }
        PlatformRelease platformRelease = findPlatformRelease(platformKey, streamKey, version);
        ExtensionRelease extensionRelease = extensionReleaseRepository
                .findByGAV(extensionGroupId, extensionArtifactId, extensionVersion)
                .orElseThrow(() -> new NotFoundException("Extension Release not found"));
        PlatformExtension platformExtension = platformExtensionRepository
                .findByPlatformReleaseAndExtensionRelease(platformRelease, extensionRelease)
                .orElseThrow(() -> new NotFoundException("Platform Extension not found"));
        // Perform changes and persist
        platformExtension.metadata = metadata;
        try {
            platformExtensionRepository.saveAndFlush(platformExtension);
            cache.clear();
        } finally {
            dbStateRepository.updateUpdatedAt();
        }
        return ResponseEntity.accepted().build();
    }

    @PatchMapping(value = "/v1/extension/{groupId}/{artifactId}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @SecurityRequirement(name = "Authentication")
    @Transactional
    @Operation(summary = "Patches an Extension")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "If invocation is successful"),
            @ApiResponse(responseCode = "404", description = "If Platform/Stream/Release were not found")
    })
    public ResponseEntity<Object> patchExtension(
            @NotNull(message = "groupId is missing") @PathVariable("groupId") String groupId,
            @NotNull(message = "artifactId is missing") @PathVariable("artifactId") String artifactId,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "description", required = false) String description) {
        Extension extension = extensionRepository.findByGA(groupId, artifactId)
                .orElseThrow(() -> new NotFoundException("Extension not found"));
        extension.name = name;
        extension.description = description;
        try {
            extensionRepository.save(extension);
            cache.clear();
        } finally {
            dbStateRepository.updateUpdatedAt();
        }
        return ResponseEntity.accepted().build();
    }

    @PatchMapping(value = "/v1/extension/{groupId}/{artifactId}/{version}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @SecurityRequirement(name = "Authentication")
    @Transactional
    @Operation(summary = "Patches an Extension Release")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "If invocation is successful"),
            @ApiResponse(responseCode = "204", description = "If metadata is empty"),
            @ApiResponse(responseCode = "404", description = "If Extension Release was not found")
    })
    public ResponseEntity<Object> patchExtensionRelease(
            @NotNull(message = "groupId is missing") @PathVariable("groupId") String groupId,
            @NotNull(message = "artifactId is missing") @PathVariable("artifactId") String artifactId,
            @NotNull(message = "version is missing") @PathVariable("version") String version,
            @NotNull(message = "metadata is required") @RequestParam(name = "metadata", required = false) String metadataJson) {
        Map<String, Object> metadata;
        if (metadataJson != null && !metadataJson.isEmpty()) {
            try {
                metadata = objectMapper.readValue(metadataJson, Map.class);
            } catch (JsonProcessingException e) {
                throw new BadRequestException("Invalid metadata JSON", e);
            }
        } else {
            // Since metadata is the only parameter, return NO_CONTENT
            return ResponseEntity.noContent().build();
        }
        ExtensionRelease extensionRelease = extensionReleaseRepository.findByGAV(groupId, artifactId, version)
                .orElseThrow(() -> new NotFoundException("Extension Release not found"));
        // Perform changes and persist
        try {
            extensionRelease.metadata = metadata;
            extensionReleaseRepository.save(extensionRelease);
            cache.clear();
        } finally {
            dbStateRepository.updateUpdatedAt();
        }
        return ResponseEntity.accepted().build();
    }

    private PlatformRelease findPlatformRelease(String platformKey, String streamKey, String version) {
        Platform platform = platformRepository.findByPlatformKey(platformKey)
                .orElseThrow(() -> new NotFoundException("Platform not found"));
        PlatformStream stream = platformStreamRepository.findByPlatformAndStreamKey(platform, streamKey)
                .orElseThrow(() -> new NotFoundException("Platform Stream not found"));
        return platformReleaseRepository.findByPlatformStreamAndVersion(stream, version)
                .orElseThrow(() -> new NotFoundException("Platform Release not found"));
    }
}
