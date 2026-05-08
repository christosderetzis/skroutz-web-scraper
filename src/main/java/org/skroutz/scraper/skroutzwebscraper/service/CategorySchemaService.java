package org.skroutz.scraper.skroutzwebscraper.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.dto.CategorySchemaCreateRequestDto;
import org.skroutz.scraper.skroutzwebscraper.dto.CategorySchemaResponseDto;
import org.skroutz.scraper.skroutzwebscraper.entity.CategorySchema;
import org.skroutz.scraper.skroutzwebscraper.exception.CategorySchemaNotFoundException;
import org.skroutz.scraper.skroutzwebscraper.exception.DuplicateCategoryException;
import org.skroutz.scraper.skroutzwebscraper.repository.CategorySchemaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategorySchemaService {

    private final CategorySchemaRepository categorySchemaRepository;

    @Transactional
    public CategorySchemaResponseDto create(CategorySchemaCreateRequestDto request) {
        if (categorySchemaRepository.existsByCategory(request.getCategory())) {
            throw new DuplicateCategoryException(request.getCategory());
        }

        CategorySchema entity = CategorySchema.builder()
                .category(request.getCategory())
                .schema(request.getSchema())
                .build();

        CategorySchema saved = categorySchemaRepository.save(entity);
        log.info("Created category schema for category: {}", saved.getCategory());
        return toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public CategorySchemaResponseDto getByCategory(String category) {
        return categorySchemaRepository.findByCategory(category)
                .map(this::toResponseDto)
                .orElseThrow(() -> new CategorySchemaNotFoundException(category));
    }

    private CategorySchemaResponseDto toResponseDto(CategorySchema entity) {
        return CategorySchemaResponseDto.builder()
                .id(entity.getId())
                .category(entity.getCategory())
                .schema(entity.getSchema())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
