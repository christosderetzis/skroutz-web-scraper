package org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PricePoint(
        LocalDate date,
        BigDecimal price
) { }
