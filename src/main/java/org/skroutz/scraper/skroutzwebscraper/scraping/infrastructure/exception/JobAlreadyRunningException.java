package org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.exception;

import org.skroutz.scraper.skroutzwebscraper.common.exception.ConflictException;
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.enums.ScrapeJobType;

public class JobAlreadyRunningException extends ConflictException {

    public JobAlreadyRunningException(ScrapeJobType jobType, Long existingJobId) {
        super(String.format("A %s job is already running (id: %s)", jobType, existingJobId));
    }
}
