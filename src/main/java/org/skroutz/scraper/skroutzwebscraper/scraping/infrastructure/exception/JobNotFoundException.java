package org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.exception;

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(Long jobId) {
        super("Scrape job not found: " + jobId);
    }
}
