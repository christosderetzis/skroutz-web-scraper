package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing;

import java.util.stream.IntStream;

import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.api.ProductApiResponseDto;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.ScraperRequestDto;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.scraper.ProductsScraper;
import org.skroutz.scraper.skroutzwebscraper.product.application.service.ProductPersistenceService;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.utils.UrlBuilder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductScraperService {

    private final ProductsScraper productsScraper;
    private final ProductPersistenceService productPersistenceService;
    private final UrlBuilder urlBuilder;

    public void scrapeProducts(ScraperRequestDto request, boolean multiplePages) {
        log.info("Starting scrape for URL: {}, multiplePages: {}, category: {}",
                request.getUrl(), multiplePages, request.getCategory());

        if (multiplePages) {
            scrapeMultiplePages(request.getUrl(), request.getCategory());
        } else {
            scrapePage(request.getUrl(), request.getCategory());
        }
    }

    private void scrapeMultiplePages(String url, String category) {
        int totalPages = getTotalPages(url);
        if (totalPages <= 0) {
            log.warn("No pages found for URL: {}", url);
            return;
        }

        log.info("Found {} pages to scrape", totalPages);

        IntStream.rangeClosed(1, totalPages)
                .mapToObj(page -> urlBuilder.buildUrlWithPage(url, page))
                .forEach(pageUrl -> scrapePage(pageUrl, category));

        log.info("Finished scraping all pages.");
    }

    private void scrapePage(String url, String category) {
        try {
            log.info("Scraping page: {}", url);
            String jsonUrl = urlBuilder.convertToJsonUrl(url);
            ProductApiResponseDto response = productsScraper.fetchProductsPage(jsonUrl);

            // Delegate mapping and saving to the persistence service
            // TODO: emit an event during modulith split and move this logic to a separate event listener
            productPersistenceService.saveOrUpdateProducts(response, category);

            log.info("Finished scraping page: {}", url);
        } catch (Exception e) {
            log.error("Failed scraping page {}: {}", url, e.getMessage(), e);
        }
    }

    private int getTotalPages(String url) {
        String jsonUrl = urlBuilder.convertToJsonUrl(url);
        ProductApiResponseDto response = productsScraper.fetchProductsPage(jsonUrl);
        return response.getPage().getTotalPages();
    }
}
