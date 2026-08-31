package io.quarkus.registry.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.quarkus.registry.app.model.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByCategoryKey(String categoryKey);
}
