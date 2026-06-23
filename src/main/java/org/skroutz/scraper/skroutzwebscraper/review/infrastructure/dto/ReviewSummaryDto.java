package org.skroutz.scraper.skroutzwebscraper.review.infrastructure.dto;

import java.util.List;

public record ReviewSummaryDto(String summary,
                               List<String> pros,
                               List<String> cons,
                               String sentiment) {
}
