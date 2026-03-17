package org.skroutz.scraper.skroutzwebscraper.scraping.event;

import org.skroutz.scraper.skroutzwebscraper.scraping.dto.ScrapedProductData;

import java.util.List;

public record ProductsScrapedEvent(List<ScrapedProductData> products) {
}
