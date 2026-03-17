package org.skroutz.scraper.skroutzwebscraper.processing.repository;

import org.skroutz.scraper.skroutzwebscraper.processing.entity.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    PriceHistory findTopByProductIdOrderByPriceDateDesc(Long productId);
}
