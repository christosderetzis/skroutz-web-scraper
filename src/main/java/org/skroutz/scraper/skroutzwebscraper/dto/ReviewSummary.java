package org.skroutz.scraper.skroutzwebscraper.dto;

import java.util.List;

public record ReviewSummary(String summary,
                            List<String> pros,
                            List<String> cons,
                            String sentiment) {
}
