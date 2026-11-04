package org.skroutz.scraper.skroutzwebscraper.priceHistory.application.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.common.utils.DateTimeUtils;
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events.PriceHistorySavedEvent;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.domain.entity.PriceHistory;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.domain.repository.PriceHistoryRepository;
import org.skroutz.scraper.skroutzwebscraper.product.ProductDto;
import org.skroutz.scraper.skroutzwebscraper.product.ProductFacade;
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events.PriceHistoryScrapeResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceHistoryEventHandler {

    private final ProductFacade productFacade;
    private final PriceHistoryRepository historyRepo;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void saveHistoryResult(PriceHistoryScrapeResult result) {
        ProductDto product = productFacade.getProductById(result.productId());

        if (result.isSuccess()) {
            // Find last timestamp recorded in DB to filter out incoming duplicates
            Timestamp lastDate = Optional.ofNullable(historyRepo.findTopByProductIdOrderByPriceDateDesc(product.id()))
                    .map(PriceHistory::getPriceDate)
                    .orElse(new Timestamp(0));

            List<PriceHistory> newRecords = result.historyItems().stream()
                    .map(item -> PriceHistory.builder()
                            .productId(product.id())
                            .price(item.value())
                            .priceDate(DateTimeUtils.convertEpochToTimestamp(item.timestamp()))
                            .storeName(item.shopName())
                            .build())
                    .filter(record -> record.getPriceDate().after(lastDate))
                    .toList();

            if (!newRecords.isEmpty()) {
                historyRepo.saveAll(newRecords);
                log.info("Saved {} new price history entries for product ID: {}", newRecords.size(), product.id());
            }

            eventPublisher.publishEvent(new PriceHistorySavedEvent(product.id()));
        } else {
            // Optional: Handle retry logic/flag adjustments here on network failure
            log.warn("Skipping DB updates for product ID {} due to previous scrape failure", result.productId());
        }

    }
}
