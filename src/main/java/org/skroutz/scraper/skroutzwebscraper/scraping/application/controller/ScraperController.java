package org.skroutz.scraper.skroutzwebscraper.scraping.application.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.AsyncScrapingFacade;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.ScrapeJobService;
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.enums.ScrapeJobType;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.ScraperRequestDto;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.ScrapeJobResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/scraper")
@RequiredArgsConstructor
@Validated
public class ScraperController {

    private final ScrapeJobService scrapeJobService;
    private final AsyncScrapingFacade asyncScrapingFacade;

    @PostMapping("/specifications")
    public ResponseEntity<ScrapeJobResponseDto> scrapeSpecifications() {
        ScrapeJobResponseDto dto = scrapeJobService.startJob(ScrapeJobType.SCRAPE_SPECIFICATIONS);
        asyncScrapingFacade.runSpecificationsScraping(dto.getId());
        return ResponseEntity.accepted().body(dto);
    }

    @PostMapping("/reviews")
    public ResponseEntity<ScrapeJobResponseDto> scrapeReviews() {
        ScrapeJobResponseDto dto = scrapeJobService.startJob(ScrapeJobType.SCRAPE_REVIEWS);
        asyncScrapingFacade.runReviewsScraping(dto.getId());
        return ResponseEntity.accepted().body(dto);
    }

    @PostMapping("/price-history")
    public ResponseEntity<ScrapeJobResponseDto> scrapePriceHistory() {
        ScrapeJobResponseDto dto = scrapeJobService.startJob(ScrapeJobType.SCRAPE_PRICE_HISTORY);
        asyncScrapingFacade.runPriceHistoryScraping(dto.getId());
        return ResponseEntity.accepted().body(dto);
    }

    @PostMapping("/products")
    public ResponseEntity<ScrapeJobResponseDto> scrapeProducts(@Valid @RequestBody ScraperRequestDto request,
                                                                @RequestParam boolean multiple) {
        ScrapeJobResponseDto dto = scrapeJobService.startJob(ScrapeJobType.SCRAPE_PRODUCTS);
        asyncScrapingFacade.runProductScraping(dto.getId(), request, multiple);
        return ResponseEntity.accepted().body(dto);
    }
}
