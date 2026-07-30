package org.skroutz.scraper.skroutzwebscraper.scraping.application.controller;

import lombok.RequiredArgsConstructor;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.ScrapeJobService;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.ScrapeJobResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class ScrapeJobController {

    private final ScrapeJobService scrapeJobService;

    @GetMapping("/{jobId}")
    public ResponseEntity<ScrapeJobResponseDto> getJob(@PathVariable Long jobId) {
        return ResponseEntity.ok(scrapeJobService.getJob(jobId));
    }
}
