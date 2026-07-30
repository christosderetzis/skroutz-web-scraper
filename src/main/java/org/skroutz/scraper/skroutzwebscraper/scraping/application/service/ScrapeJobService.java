package org.skroutz.scraper.skroutzwebscraper.scraping.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.enums.ScrapeJobStatus;
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.enums.ScrapeJobType;
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.entity.ScrapeJob;
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.repository.ScrapeJobRepository;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.ScrapeJobResponseDto;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.exception.JobAlreadyRunningException;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.exception.JobNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScrapeJobService {

    private final ScrapeJobRepository repository;

    @Value("${scraper.job.stale-threshold-hours:2}")
    private int staleThresholdHours;

    public ScrapeJobResponseDto startJob(ScrapeJobType jobType) {
        repository.findByStatus(ScrapeJobStatus.RUNNING).ifPresent(running -> {
            if (isStale(running)) {
                log.warn("Expiring stale {} job {} (started {})", running.getJobType(), running.getId(), running.getStartedAt());
                running.fail("Expired: job exceeded maximum runtime of " + staleThresholdHours + "h");
                repository.save(running);
            } else {
                throw new JobAlreadyRunningException(jobType, running.getId());
            }
        });

        ScrapeJob job = ScrapeJob.builder()
                .jobType(jobType)
                .status(ScrapeJobStatus.RUNNING)
                .build();
        ScrapeJob saved = repository.save(job);
        log.info("Started {} job {}", jobType, saved.getId());
        return ScrapeJobResponseDto.from(saved);
    }

    @Transactional
    public void completeJob(Long jobId) {
        repository.findById(jobId).ifPresent(job -> {
            job.complete();
            repository.save(job);
            log.info("Completed job {}", jobId);
        });
    }

    @Transactional
    public void failJob(Long jobId, String error) {
        repository.findById(jobId).ifPresent(job -> {
            job.fail(error);
            repository.save(job);
            log.info("Failed job {}: {}", jobId, error);
        });
    }

    @Transactional(readOnly = true)
    public ScrapeJobResponseDto getJob(Long jobId) {
        return ScrapeJobResponseDto.from(
            repository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId))
        );
    }

    private boolean isStale(ScrapeJob job) {
        return Duration.between(job.getStartedAt(), LocalDateTime.now()).toHours() >= staleThresholdHours;
    }
}
