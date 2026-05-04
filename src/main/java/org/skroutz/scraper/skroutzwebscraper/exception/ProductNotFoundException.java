package org.skroutz.scraper.skroutzwebscraper.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long productId) {
        super(String.format("Product not found with id: %d", productId));
    }
}
