package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.api.PriceHistoryResponseApiDto;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.scraper.PriceHistoryScraper;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.events.PriceHistoryScrapeResult;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.utils.UrlBuilder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Stream;

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

            PriceHistoryResponseApiDto.MetricDataDto minPrice = response.getMinPrice();

            List<PriceHistoryScrapeResult.PriceHistoryItem> items = Optional.ofNullable(response.getMinPrice())
                    .map(res -> {
                        PriceHistoryResponseApiDto.GraphDataDto gd = res.getGraphData();
                        List<PriceHistoryScrapeResult.PriceHistoryItem> result = new ArrayList<>();

                        Stream.of(gd.getAll(), gd.getOneMonth(), gd.getThreeMonths(), gd.getSixMonths())
                                .filter(Objects::nonNull)
                                .forEach(period -> result.addAll(toPriceHistoryItems(period)));

                        return result;
                    })
                    .orElse(Collections.emptyList());

            return new PriceHistoryScrapeResult(productId, items, true);

        } catch (Exception e) {
            log.error("Error network scraping price history for product ID {}: {}", productId, e.getMessage());
            return new PriceHistoryScrapeResult(productId, Collections.emptyList(), false);
        }
    }

    private List<PriceHistoryScrapeResult.PriceHistoryItem> toPriceHistoryItems(PriceHistoryResponseApiDto.TimePeriodDto period) {
        return period.getValues().stream()
                .map(data -> new PriceHistoryScrapeResult.PriceHistoryItem(
                        data.getValue(),
                        data.getTimestamp(),
                        data.getShopName()
                ))
                .toList();
    }
}
