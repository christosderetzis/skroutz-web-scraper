package org.skroutz.scraper.skroutzwebscraper.scraping.domain.repository;

import org.skroutz.scraper.skroutzwebscraper.scraping.domain.entity.ScrapeJob;
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.enums.ScrapeJobStatus;
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.enums.ScrapeJobType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScrapeJobRepository extends JpaRepository<ScrapeJob, UUID> {
    Optional<ScrapeJob> findByStatus(ScrapeJobStatus status);
}
