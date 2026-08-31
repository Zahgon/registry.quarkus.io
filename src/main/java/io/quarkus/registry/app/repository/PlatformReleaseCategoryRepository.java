package io.quarkus.registry.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.quarkus.registry.app.model.Category;
import io.quarkus.registry.app.model.PlatformRelease;
import io.quarkus.registry.app.model.PlatformReleaseCategory;

public interface PlatformReleaseCategoryRepository extends JpaRepository<PlatformReleaseCategory, Long> {

    Optional<PlatformReleaseCategory> findByPlatformReleaseAndCategory(PlatformRelease platformRelease, Category category);
}
