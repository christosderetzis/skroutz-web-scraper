package org.skroutz.scraper.skroutzwebscraper.search.application.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.search.application.service.ProductSearchService;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.ProductSuggestionDto;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.ProductSearchRequest;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.ProductSearchResponse;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.SimilarProductsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/products")
@Validated
public class ProductSearchController {

    private final ProductSearchService productSearchService;

    public ProductSearchController(ProductSearchService productSearchService) {
        this.productSearchService = productSearchService;
    }

    @GetMapping("/autocomplete")
    public List<ProductSuggestionDto> autocomplete(
            @RequestParam @NotBlank(message = "Search query is required") String q,
            @RequestParam(defaultValue = "5") @Min(value = 1, message = "Limit must be at least 1") int limit) {

        return productSearchService.getProductSuggestions(q, limit);
    }

    @PostMapping("/search")
    public ResponseEntity<ProductSearchResponse> search(@Valid @RequestBody ProductSearchRequest request) {
        return ResponseEntity.ok(productSearchService.search(request));
    }

    @GetMapping("/{id}/similar")
    public ResponseEntity<SimilarProductsResponse> findSimilar(
            @PathVariable Long id,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "Limit must be at least 1") int limit) {
        return ResponseEntity.ok(productSearchService.findSimilar(id, limit));
    }
}
