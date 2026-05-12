package org.skroutz.scraper.skroutzwebscraper.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.dto.ReviewsApiResponseDto;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.entity.Review;
import org.skroutz.scraper.skroutzwebscraper.mapper.ReviewsMapper;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.repository.ReviewRepository;
import org.skroutz.scraper.skroutzwebscraper.scraper.ReviewsScraper;
import org.skroutz.scraper.skroutzwebscraper.utils.UrlBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class ReviewsService {

    private final ReviewsScraper reviewsScraper;
    private final ReviewsMapper reviewsMapper;
    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UrlBuilder urlBuilder;
    private final TransactionTemplate transactionTemplate;

    @Value("${scraper.delays.review-page-ms:100}")    private long reviewPageDelayMs;
    @Value("${scraper.delays.reviews-ms:2000}")      private long productLoopDelayMs;

    public void parseReviews() {
        // 1. Grab every product that still needs its reviews parsed
        Slice<Product> productSlice;
        int page = 0;

        // 2. Process them one after another
        do {
            productSlice = productRepository.findAllByReviewsParsedAndRatingIsNotNull(false, PageRequest.of(page, 100));

            for (Product product : productSlice) {
                // Use transactionTemplate to ensure each product has its own transaction
                transactionTemplate.executeWithoutResult(status -> {
                    processSingleProduct(product);
                });

                sleep(productLoopDelayMs);
            }
        } while (productSlice.hasNext());
    }

    private void processSingleProduct(Product product) {
        log.info("Fetching and saving reviews for product ID: {}", product.getId());
        if (product.getUrl() == null || product.getUrl().isBlank()) return;

        try {
            // 1. Scrape all review pages
            List<ReviewsApiResponseDto.ReviewDto> dtoList = scrapeReviews(product.getUrl());

            // 2. Map DTOs → entities
            List<Review> reviews = reviewsMapper.mapToReviews(dtoList);

            if (reviews != null && !reviews.isEmpty()) {
                reviews.forEach(r -> {
                    r.setProductId(product.getId());
                    r.setProduct(product);
                });
                reviewRepository.saveAll(reviews);
                log.info("Saved {} reviews for product ID: {}", reviews.size(), product.getId());
            } else {
                log.warn("No reviews available for product ID: {}", product.getId());
            }

            // 3. Mark the product as finished and persist the flag
            product.setReviewsParsed(true);
            productRepository.save(product);
        } catch (Exception e) {
            log.error("Error processing product ID: {}. Message: {}", product.getId(), e.getMessage());
        }

    }

    private List<ReviewsApiResponseDto.ReviewDto> scrapeReviews(String productUrl) {

        log.info("Scraping reviews for {}", productUrl);

        Integer offset   = 0;
        Integer pageSize = 0;
        List<ReviewsApiResponseDto.ReviewDto> dtoList = new ArrayList<>();

        do {
            sleep(reviewPageDelayMs);   // wait before the next request

            String reviewUrl = urlBuilder.buildReviewsApiUrl(productUrl, offset);
            ReviewsApiResponseDto response = reviewsScraper.fetchReviewPage(reviewUrl);

            List<ReviewsApiResponseDto.ReviewDto> pageItems = response.getReviews().getReviews();
            dtoList.addAll(pageItems);

            pageSize = pageItems.size();
            offset   += pageSize;
        } while (pageSize > 0);

        log.info("Total reviews fetched: {}", dtoList.size());
        return dtoList;
    }

    private void sleep(long millis) {
        try {Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
