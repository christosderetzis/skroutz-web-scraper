package org.skroutz.scraper.skroutzwebscraper.repository;

import org.skroutz.scraper.skroutzwebscraper.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findAllByProductIdAndReviewTextIsNotNull(Long productId);
}