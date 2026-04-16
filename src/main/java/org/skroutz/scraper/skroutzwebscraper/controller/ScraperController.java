package org.skroutz.scraper.skroutzwebscraper.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.dto.ScraperRequestDto;
import org.skroutz.scraper.skroutzwebscraper.service.PriceHistoryService;
import org.skroutz.scraper.skroutzwebscraper.service.ProductsService;
import org.skroutz.scraper.skroutzwebscraper.service.ReviewsService;
import org.skroutz.scraper.skroutzwebscraper.service.SpecificationsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/scraper")
@RequiredArgsConstructor
public class ScraperController {

    private final SpecificationsService specificationsService;
    private final ReviewsService reviewsService;
    private final ProductsService productsService;
    private final PriceHistoryService priceHistoryService;

    @PostMapping("/specifications")
    public void scrapeSpecifications() {
        log.info("Starting specifications scraping task...");
        specificationsService.parseSpecifications();
        log.info("Specifications scraping task completed.");
    }

    @PostMapping("/reviews")
    public void scrapeReviews() {
        log.info("Starting reviews scraping task...");
        reviewsService.parseReviews();
        log.info("Reviews scraping task completed.");
    }

    @PostMapping("/price-history")
    public void scrapePriceHistory() {
        log.info("Starting price history scraping task...");
        priceHistoryService.fetchPriceHistoryForProducts();
        log.info("Price history scraping task completed.");
    }

    @PostMapping("/products")
    public ResponseEntity<Void> scrapeProducts(@RequestBody ScraperRequestDto request,
                                                @RequestParam boolean multiple) {
        log.info("Received request to scrape products from URL: {}, multiple: {}", request.getUrl(), multiple);
        productsService.scrapeProducts(request, multiple);
        return ResponseEntity.ok().build();
    }
}
