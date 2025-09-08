package org.skroutz.scraper.skroutzwebscraper.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.entity.Review;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.repository.ReviewRepository;
import org.skroutz.scraper.skroutzwebscraper.scraper.ReviewsScraper;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewsService {

    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewsScraper reviewsScraper;

    public void parseReviews() {
        List<Product> unparsedProducts = productRepository.findAllByReviewsParsed(false);

        for (Product product : unparsedProducts) {
            try {
                log.info("Parsing reviews for product: {}", product.getId());
                String url = product.getUrl();
                if (!url.isBlank()) {
                    // Assuming a ReviewsScraper class exists to handle the scraping logic
                    List<Review> reviews = reviewsScraper.scrapeReviews(url);
                    reviews.forEach(review -> {
                        review.setProductId(product.getId());
                        review.setProduct(product);
                    });
                    product.setReviewsParsed(true);
                    productRepository.save(product);
                    reviewRepository.saveAll(reviews);
                    Thread.sleep(2000); // Sleep for 2 seconds to avoid overwhelming the server
                    log.info("Successfully parsed reviews for product: {}", product.getTitle());
                } else {
                    log.warn("Product URL is empty or null for product: {}", product.getId());
                }
            } catch (Exception e) {
                log.error("Error parsing reviews for product {}: {}", product.getId(), e.getMessage(), e);
            }
        }
    }
}
