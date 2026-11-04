package org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events;

import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.api.ReviewsApiResponseDto;
import org.springframework.modulith.NamedInterface;

import java.util.List;

@NamedInterface(name = "ReviewsScrapeResult")
public record ReviewsScrapeResult(
        Long productId,
        List<ReviewsApiResponseDto.ReviewDto> reviewDtos,
        boolean isSuccess
) {}
