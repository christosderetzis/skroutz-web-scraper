package org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto;

import org.skroutz.scraper.skroutzwebscraper.scraping.domain.entity.ScrapeJob;

import java.time.LocalDateTime;

public record ScrapeJobResponseDto(
    Long id,
    String jobType,
    String status,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    String error
) {

    public static ScrapeJobResponseDto from(ScrapeJob job) {
        return new ScrapeJobResponseDto(
            job.getId(),
            job.getJobType().name(),
            job.getStatus().name(),
            job.getStartedAt(),
            job.getFinishedAt(),
            job.getError()
        );
    }
}
