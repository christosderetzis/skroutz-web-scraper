package org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.exception;

import java.util.UUID;

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(UUID jobId) {
        super("Scrape job not found: " + jobId);
    }
}
