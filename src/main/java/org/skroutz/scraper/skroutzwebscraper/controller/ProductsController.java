package org.skroutz.scraper.skroutzwebscraper.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.dto.ScraperRequestDto;
import org.skroutz.scraper.skroutzwebscraper.scraper.ProductsScraper;
import org.skroutz.scraper.skroutzwebscraper.service.ProductsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/products")
public class ProductsController {

    private final ProductsService productsService;

    public ProductsController(ProductsService productsService) {
        this.productsService = productsService;
    }

    @PostMapping("/scrape")
    ResponseEntity<Void> scrapeProducts(@RequestBody ScraperRequestDto scraperRequestDto,
                                        @RequestParam boolean multiple) {
        String baseUrl = scraperRequestDto.getUrl();

        if (multiple) {
            Integer totalPages = productsService.getNumberOfWebPages(baseUrl);
            if (totalPages <= 0) {
                log.warn("No pages found to scrape for URL: {}", baseUrl);
                return ResponseEntity.ok().build();
            }

            scrapeMultiplePages(baseUrl, totalPages);
        } else {
            log.info("Scraping single page products from URL: {}", baseUrl);
            productsService.scrapeAndSaveProducts(baseUrl);
        }

        return ResponseEntity.ok().build();
    }

    private void scrapeMultiplePages(String baseUrl, int totalPages) {
        for (int page = 1; page <= totalPages; page++) {
            String urlToScrape = buildUrlWithPage(baseUrl, page);
            log.info("Scraping products from page {} of URL: {}", page, urlToScrape);
            productsService.scrapeAndSaveProducts(urlToScrape);
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
