package org.skroutz.scraper.skroutzwebscraper.category;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.category.domain.entity.CategorySchema;
import org.skroutz.scraper.skroutzwebscraper.category.domain.repository.CategorySchemaRepository;
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.CategoryMappingSchema;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CategorySchemaFacade {

    private final CategorySchemaRepository categorySchemaRepository;

    public Map<String, CategoryMappingSchema> getAllMappingSchemas() {
        return categorySchemaRepository.findAll().stream()
                .collect(Collectors.toMap(CategorySchema::getCategory, CategorySchema::getSchema));
    }

    public CategoryMappingSchema getMappingSchemaForCategory(String category) {
        return categorySchemaRepository.findByCategory(category)
                .map(CategorySchema::getSchema)
                .orElse(null);
    }
}
