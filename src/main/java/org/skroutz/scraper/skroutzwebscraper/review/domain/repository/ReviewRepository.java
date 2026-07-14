package org.skroutz.scraper.skroutzwebscraper.review.domain.repository;

import org.skroutz.scraper.skroutzwebscraper.review.domain.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findAllByProductIdAndReviewTextIsNotNull(Long productId);

    // Dynamic native query to handle the mathematical sorting ratio safely
    @Query(
            value = "SELECT * FROM scraper_schema.reviews WHERE product_id = :productId",
            countQuery = "SELECT count(*) FROM scraper_schema.reviews WHERE product_id = :productId",
            nativeQuery = true
    )
    Page<Review> findByProductIdSorted(
            @Param("productId") Long productId,
            Pageable pageable
    );
}
