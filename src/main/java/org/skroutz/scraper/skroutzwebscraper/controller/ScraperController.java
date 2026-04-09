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
    public ResponseEntity<Void> scrapeProducts(@RequestBody ScraperRequestDto scraperRequestDto,
                                                @RequestParam boolean multiple) {
        if (multiple) {
            Integer totalPages = productsService.getNumberOfWebPages(scraperRequestDto.getUrl());
            if (totalPages <= 0) {
                log.warn("No pages found to scrape for URL: {}", scraperRequestDto.getUrl());
                return ResponseEntity.ok().build();
            }

            scrapeMultiplePages(scraperRequestDto, totalPages);
        } else {
            log.info("Scraping single page products from URL: {}", scraperRequestDto.getUrl());
            productsService.scrapeAndSaveProducts(scraperRequestDto);
        }

        return ResponseEntity.ok().build();
    }

    private void scrapeMultiplePages(ScraperRequestDto scraperRequestDto, int totalPages) {
        for (int page = 1; page <= totalPages; page++) {
            String urlToScrape = buildUrlWithPage(scraperRequestDto.getUrl(), page);
            log.info("Scraping products from page {} of URL: {}", page, urlToScrape);

            ScraperRequestDto pageRequestDto = ScraperRequestDto.builder()
                    .url(urlToScrape)
                    .category(scraperRequestDto.getCategory())
                    .build();

            productsService.scrapeAndSaveProducts(pageRequestDto);
        }
    }

    private String buildUrlWithPage(String baseUrl, int page) {
        if (page == 1) {
            return baseUrl;
        }

        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "page=" + page;
    }
}
