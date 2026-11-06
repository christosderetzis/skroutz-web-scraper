package org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.exception;

import org.skroutz.scraper.skroutzwebscraper.common.exception.NotFoundException;

public class JobNotFoundException extends NotFoundException {

    public JobNotFoundException(Long jobId) {
        super("Scrape job not found: " + jobId);
    }
}
