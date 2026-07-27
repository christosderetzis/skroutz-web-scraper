package org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.entity.ScrapeJob;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapeJobResponseDto {
    private UUID id;
    private String jobType;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String error;

    public static ScrapeJobResponseDto from(ScrapeJob job) {
        return ScrapeJobResponseDto.builder()
                .id(job.getId())
                .jobType(job.getJobType().name())
                .status(job.getStatus().name())
                .startedAt(job.getStartedAt())
                .finishedAt(job.getFinishedAt())
                .error(job.getError())
                .build();
    }
}
