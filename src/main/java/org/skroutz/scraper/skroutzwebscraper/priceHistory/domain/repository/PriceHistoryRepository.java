package org.skroutz.scraper.skroutzwebscraper.priceHistory.domain.repository;

import org.skroutz.scraper.skroutzwebscraper.priceHistory.domain.entity.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    PriceHistory findTopByProductIdOrderByPriceDateDesc(Long productId);

    @Query("""
    SELECT p
    FROM PriceHistory p
    WHERE p.product.id = :productId
      AND p.priceDate >= :from
    ORDER BY p.priceDate
""")
    List<PriceHistory> findPriceHistory(
            @Param("productId") Long productId,
            @Param("from") Timestamp from
    );
}
