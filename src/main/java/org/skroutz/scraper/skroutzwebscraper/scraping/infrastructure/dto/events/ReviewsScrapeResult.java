package org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.events;

import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.api.ReviewsApiResponseDto;

import java.util.List;

public record ReviewsScrapeResult(
        Long productId,
        List<ReviewsApiResponseDto.ReviewDto> reviewDtos,
        boolean isSuccess
) {}
