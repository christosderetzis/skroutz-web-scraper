package org.skroutz.scraper.skroutzwebscraper.review.infrastructure.dto;

import java.time.LocalDate;

public record ReviewResponseDto(
        Long id,
        String reviewerName,
        Integer reviewerRating,
        LocalDate reviewDate,
        Integer helpfulVotes,
        Integer totalVotes,
        String reviewText,
        String[] pros,
        String[] cons,
        String[] neutral,
        Boolean isVerifiedPurchase
) {}
