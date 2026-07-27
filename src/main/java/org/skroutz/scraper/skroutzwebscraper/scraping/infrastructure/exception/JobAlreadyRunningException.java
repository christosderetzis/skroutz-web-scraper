package org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.exception;

import org.skroutz.scraper.skroutzwebscraper.scraping.domain.enums.ScrapeJobType;

import java.util.UUID;

public class JobAlreadyRunningException extends RuntimeException {
    public JobAlreadyRunningException(ScrapeJobType jobType, UUID existingJobId) {
        super(String.format("A %s job is already running (id: %s)", jobType, existingJobId));
    }
}
