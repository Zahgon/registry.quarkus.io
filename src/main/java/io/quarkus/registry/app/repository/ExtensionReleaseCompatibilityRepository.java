package io.quarkus.registry.app.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.quarkus.registry.app.model.ExtensionRelease;
import io.quarkus.registry.app.model.ExtensionReleaseCompatibility;

public interface ExtensionReleaseCompatibilityRepository extends JpaRepository<ExtensionReleaseCompatibility, Long> {

    Optional<ExtensionReleaseCompatibility> findByExtensionReleaseAndQuarkusCoreVersion(ExtensionRelease extensionRelease,
            String quarkusCoreVersion);

    void deleteByExtensionReleaseAndQuarkusCoreVersion(ExtensionRelease extensionRelease, String quarkusCoreVersion);

    @Query("""
            select e.extensionRelease.id, e.compatible from ExtensionReleaseCompatibility e
            where e.quarkusCoreVersion = :quarkusCore
            """)
    List<Object[]> findCompatibility(String quarkusCore);

    default Map<Long, Boolean> findCompatibleMap(String quarkusCore) {
        return findCompatibility(quarkusCore).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Boolean) row[1]));
    }
}
