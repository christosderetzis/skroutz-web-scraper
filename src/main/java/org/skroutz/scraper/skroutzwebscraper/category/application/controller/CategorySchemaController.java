package org.skroutz.scraper.skroutzwebscraper.category.application.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.category.infrastructure.dto.CategorySchemaCreateRequestDto;
import org.skroutz.scraper.skroutzwebscraper.category.infrastructure.dto.CategorySchemaResponseDto;
import org.skroutz.scraper.skroutzwebscraper.category.application.service.CategorySchemaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/category-schemas")
@Validated
public class CategorySchemaController {

    private final CategorySchemaService categorySchemaService;

    public CategorySchemaController(CategorySchemaService categorySchemaService) {
        this.categorySchemaService = categorySchemaService;
    }

    @PostMapping
    public ResponseEntity<CategorySchemaResponseDto> create(@Valid @RequestBody CategorySchemaCreateRequestDto request) {
        CategorySchemaResponseDto response = categorySchemaService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{category}")
    public ResponseEntity<CategorySchemaResponseDto> getByCategory(@PathVariable String category) {
        CategorySchemaResponseDto response = categorySchemaService.getByCategory(category);
        return ResponseEntity.ok(response);
    }
}
