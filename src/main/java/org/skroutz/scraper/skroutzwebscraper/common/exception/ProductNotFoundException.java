package org.skroutz.scraper.skroutzwebscraper.common.exception;

public class ProductNotFoundException extends NotFoundException {
    public ProductNotFoundException(Long productId) {
        super(String.format("Product not found with id: %d", productId));
    }
}
