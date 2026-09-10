package org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PricePoint(Instant timestamp,
                         BigDecimal price) {
}
