package org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events;

import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.api.ReviewsApiResponseDto;

import java.util.List;

public record ReviewsScrapeResult(
        Long productId,
        List<ReviewsApiResponseDto.ReviewDto> reviewDtos,
        boolean isSuccess
) {}
