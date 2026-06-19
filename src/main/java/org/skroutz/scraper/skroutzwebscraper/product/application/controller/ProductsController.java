package org.skroutz.scraper.skroutzwebscraper.product.application.controller;

import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.product.application.service.ProductsService;
import org.skroutz.scraper.skroutzwebscraper.product.infrastructure.dto.ProductDetailsResponseDto;
import org.skroutz.scraper.skroutzwebscraper.search.application.service.ProductSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/products")
@Validated
public class ProductsController {

    private final ProductsService productsService;

    public ProductsController(ProductsService productsService, ProductSearchService productSearchService) {
        this.productsService = productsService;
    }

    @GetMapping("/{id}")
    ResponseEntity<ProductDetailsResponseDto> getProductDetails(@PathVariable Long id) {
        ProductDetailsResponseDto productDetails = productsService.getProductDetails(id);
        return ResponseEntity.ok(productDetails);
    }
}
