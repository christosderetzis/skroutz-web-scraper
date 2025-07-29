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
    ResponseEntity<Void> scrapeProducts(@RequestBody ScraperRequestDto scraperRequestDto, @RequestParam Boolean multiple) {
        // productsService.scrapeAndSaveProducts(scraperRequestDto.getUrl());
        if (multiple) {
            Integer pages = productsService.getNumberOfWebPages(scraperRequestDto.getUrl());
            if (pages > 0) {
                // first page does not have a page number in the URL
                log.info("Scraping products from URL: {}", scraperRequestDto.getUrl());
                productsService.scrapeAndSaveProducts(scraperRequestDto.getUrl());
                // scrape subsequent
                for (int i = 2; i <= pages; i++) {
                    // check if the URL already contains a ? keyword
                    // if it does, append &page=i, otherwise append ?page=i
                    log.info("Scraping products from page {} of URL: {}", i, scraperRequestDto.getUrl());

                    String urlWithPage = scraperRequestDto.getUrl().contains("?")
                        ? scraperRequestDto.getUrl() + "&page=" + i
                        : scraperRequestDto.getUrl() + "?page=" + i;
                    productsService.scrapeAndSaveProducts(urlWithPage);
                }
            } else {
                log.warn("No pages found to scrape for URL: {}", scraperRequestDto.getUrl());
            }
        } else {
            log.info("Scraping single page products from URL: {}", scraperRequestDto.getUrl());
            productsService.scrapeAndSaveProducts(scraperRequestDto.getUrl());
        }
        return ResponseEntity.ok().build();
    }
}
