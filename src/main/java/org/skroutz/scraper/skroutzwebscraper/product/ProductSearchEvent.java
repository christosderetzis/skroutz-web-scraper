package org.skroutz.scraper.skroutzwebscraper.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProductSearchEvent(
        List<ProductSearchEventItem> items
) {

    public record ProductSearchEventItem(
            Long id,
            String url,
            String title,
            String category,
            String brand,
            BigDecimal price,
            String imageUrl,
            String description,
            BigDecimal rating,
            Map<String, Object> specifications,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
