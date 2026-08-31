package io.quarkus.registry.app.repository;

import java.util.List;

import io.quarkus.registry.app.model.ExtensionRelease;

/**
 * Queries that are backed by the native {@code @NamedNativeQuery} definitions declared on
 * {@link ExtensionRelease}, which rely on a {@code @SqlResultSetMapping} that cannot be expressed
 * as a derived Spring Data query.
 */
public interface ExtensionReleaseRepositoryCustom {

    List<ExtensionRelease> findNonPlatformExtensions(String quarkusCore);

    List<ExtensionRelease> findLatestExtensions();
}
