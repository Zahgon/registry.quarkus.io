package io.quarkus.registry.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.quarkus.registry.app.model.ExtensionRelease;
import io.quarkus.registry.app.model.PlatformExtension;
import io.quarkus.registry.app.model.PlatformRelease;

public interface PlatformExtensionRepository extends JpaRepository<PlatformExtension, Long> {

    Optional<PlatformExtension> findByPlatformReleaseAndExtensionRelease(PlatformRelease platformRelease,
            ExtensionRelease extensionRelease);
}
