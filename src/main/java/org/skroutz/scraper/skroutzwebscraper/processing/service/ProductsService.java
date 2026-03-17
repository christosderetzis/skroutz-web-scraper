package org.skroutz.scraper.skroutzwebscraper.processing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.processing.dto.ProductDetailsResponseDto;
import org.skroutz.scraper.skroutzwebscraper.processing.mapper.ProductMapper;
import org.skroutz.scraper.skroutzwebscraper.processing.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.scraping.ScrapingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductsService {

    private final ScrapingService scrapingService;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public void scrapeAndSaveProducts(String url) {
        log.info("Starting to scrape products from URL: {}", url);
        scrapingService.scrapeProducts(url);
    }

    public Integer getNumberOfWebPages(String url) {
        log.info("Getting number of web pages for URL: {}", url);

        try {
            Integer numberOfPages = scrapingService.getNumberOfPages(url);
            log.info("Number of web pages found: {}", numberOfPages);
            return numberOfPages;
        } catch (Exception e) {
            log.error("Error getting number of web pages: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get number of web pages", e);
        }
    }

    public ProductDetailsResponseDto getProductDetails(Long id) {
        return productRepository.findById(id)
                .map(productMapper::toProductResponseDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found with id: " + id));
    }
}
