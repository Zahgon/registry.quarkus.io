package io.quarkus.registry.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.quarkus.registry.app.model.Platform;
import io.quarkus.registry.app.model.PlatformStream;

public interface PlatformStreamRepository extends JpaRepository<PlatformStream, Long> {

    Optional<PlatformStream> findByPlatformAndStreamKey(Platform platform, String streamKey);
}
