package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.api.PriceHistoryResponseApiDto;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.scraper.PriceHistoryScraper;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.events.PriceHistoryScrapeResult;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.utils.UrlBuilder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceHistoryScraperService {

    private final PriceHistoryScraper scraper;
    private final UrlBuilder urlBuilder;

    public PriceHistoryScrapeResult scrapeProductHistory(Long productId, String productUrl) {
        try {
            String url = urlBuilder.buildPriceGraphApiUrl(productUrl);
            PriceHistoryResponseApiDto response = scraper.fetchPriceHistory(url);

            List<PriceHistoryScrapeResult.PriceHistoryItem> items = Optional.ofNullable(response.getMinPrice())
                    .map(mp -> mp.getGraphData().getAll().getValues())
                    .orElse(Collections.emptyList())
                    .stream()
                    .map(data -> new PriceHistoryScrapeResult.PriceHistoryItem(
                            data.getValue(),
                            data.getTimestamp(),
                            data.getShopName()
                    ))
                    .toList();

            return new PriceHistoryScrapeResult(productId, items, true);

        } catch (Exception e) {
            log.error("Error network scraping price history for product ID {}: {}", productId, e.getMessage());
            return new PriceHistoryScrapeResult(productId, Collections.emptyList(), false);
        }
    }
}
