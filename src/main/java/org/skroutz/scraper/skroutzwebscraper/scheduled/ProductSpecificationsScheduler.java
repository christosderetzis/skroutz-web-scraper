package org.skroutz.scraper.skroutzwebscraper.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.service.ProductSpecificationsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProductSpecificationsScheduler {

    private final ProductSpecificationsService productSpecificationsService;

    @Scheduled(fixedRate = 2 * 60 * 60 * 1000)
    public void parseSpecifications() {
        log.info("Starting specifications parsing task...");
        productSpecificationsService.parseSpecifications();
        log.info("Specifications parsing task completed.");
    }
}
