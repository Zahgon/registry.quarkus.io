package io.quarkus.registry.app.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import io.quarkus.registry.app.model.DbState;

public interface DbStateRepository extends JpaRepository<DbState, Long> {

    /**
     * There will be only one row in this table
     */
    @Query(name = "DbState.findUpdatedAt")
    Instant findUpdatedAt();

    @Modifying
    @Query(value = DbState.UPDATE_SQL, nativeQuery = true)
    void updateUpdatedAt();
}
