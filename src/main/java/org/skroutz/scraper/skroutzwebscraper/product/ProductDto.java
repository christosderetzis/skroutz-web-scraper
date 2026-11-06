package org.skroutz.scraper.skroutzwebscraper.product;

public record ProductDto(
        Long id,
        String url,
        String title,
        String description,
        String category
) {
}
