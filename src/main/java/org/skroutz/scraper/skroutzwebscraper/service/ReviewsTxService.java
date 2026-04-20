package org.skroutz.scraper.skroutzwebscraper.service;

import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.dto.ReviewsApiResponseDto;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.entity.Review;
import org.skroutz.scraper.skroutzwebscraper.mapper.ReviewsMapper;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.repository.ReviewRepository;
import org.skroutz.scraper.skroutzwebscraper.scraper.ReviewsScraper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ReviewsTxService {

    private final ReviewsScraper reviewsScraper;
    private final ReviewsMapper reviewsMapper;
    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final long reviewPageDelayMs;

    public ReviewsTxService(ReviewsScraper reviewsScraper,
                            ReviewsMapper reviewsMapper,
                            ReviewRepository reviewRepository,
                            ProductRepository productRepository,
                            @Value("${scraper.delays.review-page-ms:100}") long reviewPageDelayMs) {
        this.reviewsScraper = reviewsScraper;
        this.reviewsMapper = reviewsMapper;
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.reviewPageDelayMs = reviewPageDelayMs;
    }

    @Transactional
    public void processSingleProduct(Product product) throws InterruptedException {
        log.info("Fetching and saving reviews for product ID: {}", product.getId());

        List<ReviewsApiResponseDto.ReviewDto> reviewDtos = scrapeReviews(product.getUrl());
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

    private List<ReviewsApiResponseDto.ReviewDto> scrapeReviews(String url) throws InterruptedException {
        log.info("Scraping reviews for {}", url);

        Integer offset = 0;
        Integer pageSize;
        List<ReviewsApiResponseDto.ReviewDto> reviewDtos = new ArrayList<>();
        String reviewUrl = buildReviewUrl(url, offset);

        do {
            Thread.sleep(reviewPageDelayMs);
            ReviewsApiResponseDto response = reviewsScraper.fetchReviewPage(reviewUrl);
            List<ReviewsApiResponseDto.ReviewDto> reviewPageItems = response.getReviews().getReviews();
            reviewDtos.addAll(reviewPageItems);
            pageSize = reviewPageItems.size();
            offset += pageSize;
            reviewUrl = buildReviewUrl(url, offset);
        } while (pageSize > 0);

        log.info("Total reviews fetched: {}", reviewDtos.size());
        return reviewDtos;
    }

    private String buildReviewUrl(String productUrl, Integer offset) {
        int htmlIndex = productUrl.indexOf(".html");
        if (htmlIndex != -1) {
            productUrl = productUrl.substring(0, htmlIndex);
        }
        return "%s/reviews.json?offset=%d".formatted(productUrl, offset);
    }
}
