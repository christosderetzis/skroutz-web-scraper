package org.skroutz.scraper.skroutzwebscraper.review.infrastructure.enums;

public enum ReviewSortType {
    RECENT,
    HELPFUL;

    public static ReviewSortType fromString(String value) {
        if (value == null) return RECENT;
        return switch (value.toLowerCase().trim().replaceAll("[\"\\[\\]]", "")) {
            case "helpful", "most_helpful" -> HELPFUL;
            default -> RECENT; // Default fallback or you can throw an exception
        };
    }
}
