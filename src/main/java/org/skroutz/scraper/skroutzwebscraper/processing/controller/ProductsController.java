package org.skroutz.scraper.skroutzwebscraper.processing.controller;

import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.processing.dto.ProductDetailsResponseDto;
import org.skroutz.scraper.skroutzwebscraper.processing.service.ProductsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/products")
public class ProductsController {

    private final ProductsService productsService;

    public ProductsController(ProductsService productsService) {
        this.productsService = productsService;
    }

    @GetMapping("/{id}")
    ResponseEntity<ProductDetailsResponseDto> getProductDetails(@PathVariable Long id) {
        ProductDetailsResponseDto productDetails = productsService.getProductDetails(id);
        return ResponseEntity.ok(productDetails);
    }
}
