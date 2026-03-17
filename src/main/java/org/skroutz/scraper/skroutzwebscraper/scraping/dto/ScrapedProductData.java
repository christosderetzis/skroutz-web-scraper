package org.skroutz.scraper.skroutzwebscraper.scraping.dto;

import java.math.BigDecimal;

public record ScrapedProductData(
        String title,
        String url,
        BigDecimal price,
        String description,
        BigDecimal rating,
        String imageUrl) {
}
