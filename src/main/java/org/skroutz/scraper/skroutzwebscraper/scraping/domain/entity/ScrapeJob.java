package org.skroutz.scraper.skroutzwebscraper.scraping.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.enums.ScrapeJobStatus;
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.enums.ScrapeJobType;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "scrape_jobs", schema = "scraper_schema")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScrapeJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 50, updatable = false)
    private ScrapeJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScrapeJobStatus status;

    @Column(name = "started_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private LocalDateTime startedAt;

    @Column(name = "finished_at", columnDefinition = "TIMESTAMPTZ")
    private LocalDateTime finishedAt;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    @PrePersist
    protected void onCreate() {
        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
    }

    public void complete() {
        this.status = ScrapeJobStatus.COMPLETED;
        this.finishedAt = LocalDateTime.now();
    }

    public void fail(String errorMessage) {
        this.status = ScrapeJobStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
        this.error = errorMessage;
    }
}
