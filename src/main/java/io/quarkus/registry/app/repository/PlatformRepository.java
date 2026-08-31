package io.quarkus.registry.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.quarkus.registry.app.model.Platform;

public interface PlatformRepository extends JpaRepository<Platform, Long> {

    Optional<Platform> findByPlatformKey(String platformKey);

    @Query("FROM Platform p WHERE p.platformKey like :platformKey AND p.platformType = :type")
    List<Platform> findByPlatformKeyLikeAndType(@Param("platformKey") String platformKey,
            @Param("type") Platform.Type type);

    default List<Platform> findMembersByPlatform(Platform platform) {
        return findByPlatformKeyLikeAndType(platform.platformKey + ":%", Platform.Type.M);
    }
}
