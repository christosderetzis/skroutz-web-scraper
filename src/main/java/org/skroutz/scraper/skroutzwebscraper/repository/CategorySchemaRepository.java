package org.skroutz.scraper.skroutzwebscraper.repository;

import org.skroutz.scraper.skroutzwebscraper.entity.CategorySchema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategorySchemaRepository extends JpaRepository<CategorySchema, Long> {

    Optional<CategorySchema> findByCategory(String category);

    boolean existsByCategory(String category);
}
