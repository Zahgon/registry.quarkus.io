package io.quarkus.registry.app.admin;

import static io.quarkus.registry.catalog.Extension.MD_BUILT_WITH_QUARKUS_CORE;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.registry.app.events.ExtensionCatalogDeleteEvent;
import io.quarkus.registry.app.events.ExtensionCatalogImportEvent;
import io.quarkus.registry.app.events.ExtensionCompatibilityCreateEvent;
import io.quarkus.registry.app.events.ExtensionCompatibleDeleteEvent;
import io.quarkus.registry.app.events.ExtensionCreateEvent;
import io.quarkus.registry.app.events.ExtensionDeleteEvent;
import io.quarkus.registry.app.events.ExtensionReleaseDeleteEvent;
import io.quarkus.registry.app.model.Category;
import io.quarkus.registry.app.model.Extension;
import io.quarkus.registry.app.model.ExtensionRelease;
import io.quarkus.registry.app.model.ExtensionReleaseCompatibility;
import io.quarkus.registry.app.model.Platform;
import io.quarkus.registry.app.model.PlatformExtension;
import io.quarkus.registry.app.model.PlatformRelease;
import io.quarkus.registry.app.model.PlatformReleaseCategory;
import io.quarkus.registry.app.model.PlatformStream;
import io.quarkus.registry.app.repository.CategoryRepository;
import io.quarkus.registry.app.repository.DbStateRepository;
import io.quarkus.registry.app.repository.ExtensionReleaseCompatibilityRepository;
import io.quarkus.registry.app.repository.ExtensionReleaseRepository;
import io.quarkus.registry.app.repository.ExtensionRepository;
import io.quarkus.registry.app.repository.PlatformReleaseCategoryRepository;
import io.quarkus.registry.app.repository.PlatformReleaseRepository;
import io.quarkus.registry.app.repository.PlatformRepository;
import io.quarkus.registry.app.repository.PlatformStreamRepository;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.util.PlatformArtifacts;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Administrative operations on the database
 */
@Service
public class AdminService {

    private static final Logger LOG = LoggerFactory.getLogger(AdminService.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final CategoryRepository categoryRepository;

    private final DbStateRepository dbStateRepository;

    private final ExtensionRepository extensionRepository;

    private final ExtensionReleaseRepository extensionReleaseRepository;

    private final ExtensionReleaseCompatibilityRepository extensionReleaseCompatibilityRepository;

    private final PlatformRepository platformRepository;

    private final PlatformStreamRepository platformStreamRepository;

    private final PlatformReleaseRepository platformReleaseRepository;

    private final PlatformReleaseCategoryRepository platformReleaseCategoryRepository;

    public AdminService(CategoryRepository categoryRepository, DbStateRepository dbStateRepository,
            ExtensionRepository extensionRepository, ExtensionReleaseRepository extensionReleaseRepository,
            ExtensionReleaseCompatibilityRepository extensionReleaseCompatibilityRepository,
            PlatformRepository platformRepository, PlatformStreamRepository platformStreamRepository,
            PlatformReleaseRepository platformReleaseRepository,
            PlatformReleaseCategoryRepository platformReleaseCategoryRepository) {
        this.categoryRepository = categoryRepository;
        this.dbStateRepository = dbStateRepository;
        this.extensionRepository = extensionRepository;
        this.extensionReleaseRepository = extensionReleaseRepository;
        this.extensionReleaseCompatibilityRepository = extensionReleaseCompatibilityRepository;
        this.platformRepository = platformRepository;
        this.platformStreamRepository = platformStreamRepository;
        this.platformReleaseRepository = platformReleaseRepository;
        this.platformReleaseCategoryRepository = platformReleaseCategoryRepository;
    }

    @Transactional
    public void onExtensionCatalogImport(ExtensionCatalogImportEvent event) {
        try {
            ExtensionCatalog extensionCatalog = event.extensionCatalog();
            Platform platform = platformRepository.findByPlatformKey(event.platformKey()).orElseGet(() -> {
                Platform p = new Platform();
                p.platformKey = event.platformKey();
                ArtifactCoords catalogId = ArtifactCoords.fromString(extensionCatalog.getId());
                p.name = Platform.toPlatformName(event.platformKey());
                p.groupId = catalogId.getGroupId();
                p.artifactId = catalogId.getArtifactId();
                p.platformType = event.type();
                return platformRepository.save(p);
            });
            PlatformRelease platformRelease = insertPlatformRelease(platform, extensionCatalog, event.pinned());
            for (io.quarkus.registry.catalog.Extension extension : extensionCatalog.getExtensions()) {
                insertExtensionRelease(extension, platformRelease);
            }
            //Add Categories
            for (io.quarkus.registry.catalog.Category category : extensionCatalog.getCategories()) {
                categoryRepository.findByCategoryKey(category.getId()).ifPresent(c -> {
                    if (platformReleaseCategoryRepository.findByPlatformReleaseAndCategory(platformRelease, c)
                            .isEmpty()) {
                        PlatformReleaseCategory prc = new PlatformReleaseCategory();
                        prc.platformRelease = platformRelease;
                        prc.category = c;
                        prc.metadata = category.getMetadata();
                        platformRelease.categories.add(prc);
                        platformReleaseCategoryRepository.save(prc);
                    }
                });
            }
            dbStateRepository.updateUpdatedAt();
        } catch (Exception e) {
            LOG.error("Error while inserting platform", e);
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private PlatformRelease insertPlatformRelease(Platform platform, ExtensionCatalog extensionCatalog, boolean pinned) {
        Map<String, Object> platformReleaseMetadata = (Map<String, Object>) extensionCatalog.getMetadata()
                .get("platform-release");
        String streamKey = (String) platformReleaseMetadata.get("stream");
        String version = (String) platformReleaseMetadata.get("version");
        List<String> memberBoms = (List<String>) platformReleaseMetadata.get("members");
        PlatformStream platformStream = platformStreamRepository.findByPlatformAndStreamKey(platform, streamKey)
                .orElseGet(() -> platformStreamRepository.save(new PlatformStream(platform, streamKey)));
        PlatformRelease platformRelease = platformReleaseRepository
                .findByPlatformStreamAndVersion(platformStream, version)
                .orElseGet(() -> {
                    PlatformRelease newPlatformRelease = new PlatformRelease(platformStream, version, pinned);
                    newPlatformRelease.bom = extensionCatalog.getBom().toString();
                    newPlatformRelease.quarkusCoreVersion = extensionCatalog.getQuarkusCoreVersion();
                    newPlatformRelease.upstreamQuarkusCoreVersion = extensionCatalog.getUpstreamQuarkusCoreVersion();
                    newPlatformRelease.memberBoms.addAll(memberBoms.stream().map(ArtifactCoords::fromString)
                            .map(PlatformArtifacts::ensureBomArtifact)
                            .map(ArtifactCoords::toString).toList());
                    newPlatformRelease.metadata = extensionCatalog.getMetadata();
                    return newPlatformRelease;
                });
        platformRelease.pinned = pinned;
        return platformReleaseRepository.saveAndFlush(platformRelease);
    }

    @Transactional
    public void onExtensionCreate(ExtensionCreateEvent event) {
        // Non-platform extension
        try {
            insertExtensionRelease(event.extension(), null);
            dbStateRepository.updateUpdatedAt();
        } catch (Exception e) {
            LOG.error("Error while inserting extension", e);
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public void onExtensionDelete(ExtensionDeleteEvent event) {
        // Non-platform extension
        try {
            Extension extension = event.extension();
            // Reattach extension
            extension = entityManager.merge(extension);
            extensionRepository.delete(extension);
            dbStateRepository.updateUpdatedAt();
        } catch (Exception e) {
            LOG.error("Error while deleting extension", e);
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public void onExtensionCatalogDelete(ExtensionCatalogDeleteEvent event) {
        try {
            PlatformRelease platformRelease = event.platformRelease();
            // Reattach platform release
            platformRelease = entityManager.merge(platformRelease);
            platformReleaseRepository.delete(platformRelease);
            dbStateRepository.updateUpdatedAt();
        } catch (Exception e) {
            LOG.error("Error while deleting extension catalog", e);
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public void onExtensionReleaseDelete(ExtensionReleaseDeleteEvent event) {
        // Non-platform extension
        try {
            ExtensionRelease extensionRelease = event.extensionRelease();
            // Reattach extension
            extensionRelease = entityManager.merge(extensionRelease);
            extensionReleaseRepository.delete(extensionRelease);
            dbStateRepository.updateUpdatedAt();
        } catch (Exception e) {
            LOG.error("Error while deleting extension", e);
            throw new IllegalStateException(e);
        }
    }

    private ExtensionRelease insertExtensionRelease(io.quarkus.registry.catalog.Extension ext,
            PlatformRelease platformRelease) {
        ArtifactCoords coords = ext.getArtifact();
        final String groupId = coords.getGroupId();
        final String artifactId = coords.getArtifactId();
        final String version = coords.getVersion();
        final Extension extension = extensionRepository.findByGA(groupId, artifactId)
                .orElseGet(() -> {
                    Extension newExtension = new Extension();
                    newExtension.groupId = coords.getGroupId();
                    newExtension.artifactId = artifactId;
                    newExtension.name = ext.getName();
                    newExtension.description = ext.getDescription();

                    return extensionRepository.save(newExtension);
                });

        // Name and description might have changed
        extension.name = ext.getName();
        extension.description = ext.getDescription();
        extensionRepository.save(extension);

        return extensionReleaseRepository.findByGAV(groupId, artifactId, version)
                .orElseGet(() -> {
                    ExtensionRelease newExtensionRelease = new ExtensionRelease();
                    newExtensionRelease.version = version;
                    newExtensionRelease.extension = extension;
                    String quarkusCore = (String) ext.getMetadata().get(MD_BUILT_WITH_QUARKUS_CORE);
                    // Some extensions were published using the full GAV
                    if (quarkusCore == null) {
                        // Cannot determine Quarkus version
                        quarkusCore = "0.0.0";
                    } else if (quarkusCore.contains(":")) {
                        try {
                            quarkusCore = ArtifactCoords.fromString(quarkusCore).getVersion();
                        } catch (IllegalArgumentException iae) {
                            // ignore
                        }
                    }
                    newExtensionRelease.quarkusCoreVersion = quarkusCore;
                    // Many-to-many
                    if (platformRelease != null) {
                        PlatformExtension platformExtension = new PlatformExtension();
                        platformExtension.extensionRelease = newExtensionRelease;
                        platformExtension.platformRelease = platformRelease;
                        platformExtension.metadata = ext.getMetadata();

                        platformRelease.extensions.add(platformExtension);
                        newExtensionRelease.platforms.add(platformExtension);
                    } else {
                        newExtensionRelease.metadata = ext.getMetadata();
                    }
                    return extensionReleaseRepository.save(newExtensionRelease);
                });
    }

    @Transactional
    public void onExtensionCompatibilityCreate(ExtensionCompatibilityCreateEvent event) {
        ExtensionReleaseCompatibility extensionReleaseCompatibility = extensionReleaseCompatibilityRepository
                .findByExtensionReleaseAndQuarkusCoreVersion(event.extensionRelease(), event.quarkusCore())
                .orElseGet(() -> {
                    ExtensionReleaseCompatibility newEntity = new ExtensionReleaseCompatibility();
                    newEntity.extensionRelease = event.extensionRelease();
                    newEntity.quarkusCoreVersion = event.quarkusCore();
                    return newEntity;
                });
        extensionReleaseCompatibility.compatible = event.compatible();
        extensionReleaseCompatibilityRepository.saveAndFlush(extensionReleaseCompatibility);
        dbStateRepository.updateUpdatedAt();
    }

    @Transactional
    public void onExtensionCompatibilityDelete(ExtensionCompatibleDeleteEvent event) {
        extensionReleaseCompatibilityRepository.deleteByExtensionReleaseAndQuarkusCoreVersion(
                event.extensionRelease(),
                event.quarkusCore());
        dbStateRepository.updateUpdatedAt();
    }
}
