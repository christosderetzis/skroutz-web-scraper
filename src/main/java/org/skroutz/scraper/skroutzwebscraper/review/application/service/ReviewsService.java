package org.skroutz.scraper.skroutzwebscraper.review.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.common.dto.PagedResponse;
import org.skroutz.scraper.skroutzwebscraper.product.ProductFacade;
import org.skroutz.scraper.skroutzwebscraper.product.infrastructure.exception.ProductNotFoundException;
import org.skroutz.scraper.skroutzwebscraper.review.domain.entity.Review;
import org.skroutz.scraper.skroutzwebscraper.review.domain.repository.ReviewRepository;
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.dto.ReviewResponseDto;
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.enums.ReviewSortType;
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.mapper.ReviewsMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewsService {

    private final ReviewRepository reviewRepository;
    private final ProductFacade productFacade;
    private final ReviewsMapper reviewsMapper;

    @Transactional(readOnly = true)
    public PagedResponse<ReviewResponseDto> getReviewsByProductId(Long productId, int page, int size, ReviewSortType sortType) {
        log.info("Fetching paginated reviews for product ID: {} with sorting: {}", productId, sortType);

        if (!productFacade.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }

        // 1. Determine the sort configuration dynamically based on the enum
        Sort sort;
        if (sortType == ReviewSortType.HELPFUL) {
            // Skroutz style: Priority to raw upvote count, then recency, then unique ID tie-breaker
            sort = Sort.by(Sort.Direction.DESC, "helpfulVotes")
                    .and(Sort.by(Sort.Direction.DESC, "reviewDate"))
                    .and(Sort.by(Sort.Direction.DESC, "id"));
        } else {
            // Fall back to standard chronologically descending sequence
            sort = Sort.by(Sort.Direction.DESC, "reviewDate")
                    .and(Sort.by(Sort.Direction.DESC, "id"));
        }

        // 2. Create a single, uniformly handled pageable object
        Pageable pageable = PageRequest.of(page, size, sort);

        // 3. Fetch data via the index-friendly repository method
        Page<Review> reviewPage = reviewRepository.findByProductId(productId, pageable);

        return PagedResponse.from(reviewPage.map(reviewsMapper::toResponseDto));
    }
}
