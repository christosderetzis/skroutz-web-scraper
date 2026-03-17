package org.skroutz.scraper.skroutzwebscraper.scraping.event;

import org.skroutz.scraper.skroutzwebscraper.scraping.dto.ReviewsApiResponseDto;

import java.util.List;

public record ReviewsScrapedEvent(Long productId, List<ReviewsApiResponseDto.ReviewDto> reviews) {
}
