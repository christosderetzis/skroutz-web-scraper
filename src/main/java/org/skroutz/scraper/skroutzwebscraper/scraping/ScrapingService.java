package org.skroutz.scraper.skroutzwebscraper.scraping;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.scraping.dto.PriceHistoryResponseDto;
import org.skroutz.scraper.skroutzwebscraper.scraping.dto.ReviewsApiResponseDto;
import org.skroutz.scraper.skroutzwebscraper.scraping.dto.ScrapedProductData;
import org.skroutz.scraper.skroutzwebscraper.scraping.event.PriceHistoryScrapedEvent;
import org.skroutz.scraper.skroutzwebscraper.scraping.event.ProductsScrapedEvent;
import org.skroutz.scraper.skroutzwebscraper.scraping.event.ReviewsScrapedEvent;
import org.skroutz.scraper.skroutzwebscraper.scraping.event.SpecificationsScrapedEvent;
import org.skroutz.scraper.skroutzwebscraper.scraping.scraper.PriceHistoryScraper;
import org.skroutz.scraper.skroutzwebscraper.scraping.scraper.ProductsScraper;
import org.skroutz.scraper.skroutzwebscraper.scraping.scraper.ReviewsScraper;
import org.skroutz.scraper.skroutzwebscraper.scraping.scraper.SpecificationsScraper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScrapingService {

    private final ProductsScraper productsScraper;
    private final ReviewsScraper reviewsScraper;
    private final PriceHistoryScraper priceHistoryScraper;
    private final SpecificationsScraper specificationsScraper;
    private final ApplicationEventPublisher eventPublisher;

    public void scrapeProducts(String url) {
        log.info("Scraping products from URL: {}", url);
        List<ScrapedProductData> products = productsScraper.scrapeProducts(url);
        log.info("Scraped {} products, publishing event", products.size());
        eventPublisher.publishEvent(new ProductsScrapedEvent(products));
    }

    public Integer getNumberOfPages(String url) {
        log.info("Getting number of web pages for URL: {}", url);
        return productsScraper.getNumberOfPages(url);
    }

    public void scrapeReviews(Long productId, String productUrl) throws InterruptedException {
        log.info("Scraping reviews for product ID: {}", productId);
        List<ReviewsApiResponseDto.ReviewDto> reviews = reviewsScraper.scrapeReviews(productUrl);
        eventPublisher.publishEvent(new ReviewsScrapedEvent(productId, reviews));
    }

    public void scrapePriceHistory(Long productId, String productUrl) {
        log.info("Scraping price history for product ID: {}", productId);
        String apiUrl = buildPriceGraphUrl(productUrl);
        PriceHistoryResponseDto response = priceHistoryScraper.fetchPriceHistory(apiUrl);
        eventPublisher.publishEvent(new PriceHistoryScrapedEvent(productId, response));
    }

    public void scrapeSpecifications(Long productId, String productUrl) {
        log.info("Scraping specifications for product ID: {}", productId);
        String formattedUrl = productUrl.contains("?") ? productUrl + "&lang=en" : productUrl + "?lang=en";
        JsonNode specifications = specificationsScraper.scrapeSpecifications(formattedUrl);
        eventPublisher.publishEvent(new SpecificationsScrapedEvent(productId, specifications));
    }

    private String buildPriceGraphUrl(String productUrl) {
        int htmlIndex = productUrl.indexOf(".html");
        if (htmlIndex != -1) {
            productUrl = productUrl.substring(0, htmlIndex);
        }
        return productUrl + "/price_graph.json?shipping_country=GR&currency=EUR";
    }
}
