package org.skroutz.scraper.skroutzwebscraper.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.service.SpecificationsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SpecificationsScheduler {

    private final SpecificationsService specificationsService;

    // @Scheduled(fixedRate = 2 * 60 * 60 * 1000)
    public void parseSpecifications() {
        log.info("Starting specifications parsing task...");
        specificationsService.parseSpecifications();
        log.info("Specifications parsing task completed.");
    }
}
