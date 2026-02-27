package org.skroutz.scraper.skroutzwebscraper.service;

import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.dto.ReviewsApiResponseDto;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.entity.Review;
import org.skroutz.scraper.skroutzwebscraper.mapper.ReviewsMapper;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.repository.ReviewRepository;
import org.skroutz.scraper.skroutzwebscraper.scraper.ReviewsScraper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class ReviewsTxService {

    private final ReviewsScraper reviewsScraper;
    private final ReviewsMapper reviewsMapper;
    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;

    public ReviewsTxService(ReviewsScraper reviewsScraper,
                            ReviewsMapper reviewsMapper,
                            ReviewRepository reviewRepository,
                            ProductRepository productRepository) {
        this.reviewsScraper = reviewsScraper;
        this.reviewsMapper = reviewsMapper;
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public void processSingleProduct(Product product) throws InterruptedException {
        log.info("Fetching and saving reviews for product ID: {}", product.getId());

        List<ReviewsApiResponseDto.ReviewDto> reviewDtos = reviewsScraper.scrapeReviews(product.getUrl());
        List<Review> reviews = reviewsMapper.mapToReviews(reviewDtos);

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
