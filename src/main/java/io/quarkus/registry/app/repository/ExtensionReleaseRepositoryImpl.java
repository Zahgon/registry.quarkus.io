package io.quarkus.registry.app.repository;

import java.util.List;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import io.quarkus.registry.app.model.ExtensionRelease;
import io.quarkus.registry.app.util.Version;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public class ExtensionReleaseRepositoryImpl implements ExtensionReleaseRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public List<ExtensionRelease> findNonPlatformExtensions(String quarkusCore) {
        DefaultArtifactVersion artifactVersion = new DefaultArtifactVersion(quarkusCore);
        String lowerBound = artifactVersion.getMajorVersion() + ".0.0.A";
        String upperBound = (artifactVersion.getMajorVersion() + 1) + ".0.0.A";
        List<Object[]> results = entityManager.createNamedQuery("ExtensionRelease.findNonPlatformExtensions")
                .setParameter("lowerBound", Version.toSortable(lowerBound))
                .setParameter("upperBound", Version.toSortable(upperBound))
                .setParameter("quarkusCore", quarkusCore)
                .setParameter("quarkusCoreSortable", Version.toSortable(quarkusCore))
                .getResultList();

        return results.stream().map(r -> (ExtensionRelease) r[0]).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ExtensionRelease> findLatestExtensions() {
        List<Object[]> results = entityManager.createNamedQuery("ExtensionRelease.findLatestExtensions")
                .getResultList();

        return results.stream().map(r -> (ExtensionRelease) r[0]).toList();
    }
}
