package org.skroutz.scraper.skroutzwebscraper.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.dto.ProductDetailsResponseDto;
import org.skroutz.scraper.skroutzwebscraper.dto.search.ProductSearchRequest;
import org.skroutz.scraper.skroutzwebscraper.dto.search.ProductSearchResponse;
import org.skroutz.scraper.skroutzwebscraper.dto.ProductSuggestionDto;
import org.skroutz.scraper.skroutzwebscraper.service.ProductSearchService;
import org.skroutz.scraper.skroutzwebscraper.service.ProductsService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/products")
@Validated
public class ProductsController {

    private final ProductsService productsService;
    private final ProductSearchService productSearchService;

    public ProductsController(ProductsService productsService, ProductSearchService productSearchService) {
        this.productsService = productsService;
        this.productSearchService = productSearchService;
    }

    @GetMapping("/{id}")
    ResponseEntity<ProductDetailsResponseDto> getProductDetails(@PathVariable Long id) {
        ProductDetailsResponseDto productDetails = productsService.getProductDetails(id);
        return ResponseEntity.ok(productDetails);
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
}
