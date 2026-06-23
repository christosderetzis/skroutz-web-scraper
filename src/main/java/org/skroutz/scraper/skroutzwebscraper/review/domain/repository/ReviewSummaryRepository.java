package org.skroutz.scraper.skroutzwebscraper.review.domain.repository;

import org.skroutz.scraper.skroutzwebscraper.review.domain.entity.ReviewSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewSummaryRepository extends JpaRepository<ReviewSummary, Long> {
    Optional<ReviewSummary> findByProductId(Long productId);
}
