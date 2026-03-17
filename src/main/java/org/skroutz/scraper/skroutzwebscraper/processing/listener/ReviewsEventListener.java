package org.skroutz.scraper.skroutzwebscraper.processing.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.processing.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.processing.entity.Review;
import org.skroutz.scraper.skroutzwebscraper.processing.mapper.ReviewsMapper;
import org.skroutz.scraper.skroutzwebscraper.processing.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.processing.repository.ReviewRepository;
import org.skroutz.scraper.skroutzwebscraper.scraping.event.ReviewsScrapedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewsEventListener {

    private final ReviewsMapper reviewsMapper;
    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;

    @EventListener
    @Transactional
    public void handleReviewsScraped(ReviewsScrapedEvent event) {
        log.info("Received ReviewsScrapedEvent for product ID: {}", event.productId());

        Product product = productRepository.findById(event.productId())
                .orElseThrow(() -> new IllegalStateException("Product not found: " + event.productId()));

        List<Review> reviews = reviewsMapper.mapToReviews(event.reviews());

        if (!reviews.isEmpty()) {
            reviews.forEach(review -> {
                review.setProductId(product.getId());
                review.setProduct(product);
            });
            reviewRepository.saveAll(reviews);
            log.info("Saved {} reviews for product ID: {}", reviews.size(), product.getId());
        } else {
            log.warn("No reviews available for product ID: {}", product.getId());
        }

        product.setReviewsParsed(true);
        productRepository.save(product);
    }
}
