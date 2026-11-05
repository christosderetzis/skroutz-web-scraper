package org.skroutz.scraper.skroutzwebscraper.review.application.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.product.ProductDto;
import org.skroutz.scraper.skroutzwebscraper.product.ProductFacade;
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events.ReviewsSavedEvent;
import org.skroutz.scraper.skroutzwebscraper.review.domain.entity.Review;
import org.skroutz.scraper.skroutzwebscraper.review.domain.repository.ReviewRepository;
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.mapper.ReviewsMapper;
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events.ReviewsScrapeResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewsEventHandler {

    private final ProductFacade productFacade;
    private final ReviewRepository reviewRepository;
    private final ReviewsMapper reviewsMapper;
    private final ApplicationEventPublisher eventPublisher;

    @ApplicationModuleListener
    public void saveReviewsResult(ReviewsScrapeResult result) {
        ProductDto product = productFacade.getProductById(result.productId());

        if (result.isSuccess()) {
            List<Review> reviews = reviewsMapper.mapToReviews(result.reviewDtos());

            if (reviews != null && !reviews.isEmpty()) {
                reviews.forEach(r -> {
                    r.setProductId(product.id());
                });
                reviewRepository.saveAll(reviews);

                log.info("Saved {} reviews to DB for product ID: {}", reviews.size(), product.id());
            } else {
                log.warn("No reviews payload found to convert for product ID: {}", product.id());
            }

            // Publish an event to notify that reviews have been saved for this product
            eventPublisher.publishEvent(new ReviewsSavedEvent(product.id()));
        } else {
            log.warn("Scraping failed for product ID: {}. Skipping review database entry.", product.id());
            // Optional: Set a retry counter limit or flag adjustment here if needed
        }
    }
}
