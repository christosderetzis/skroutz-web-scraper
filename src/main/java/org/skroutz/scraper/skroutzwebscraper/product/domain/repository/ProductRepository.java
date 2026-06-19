package org.skroutz.scraper.skroutzwebscraper.product.domain.repository;

import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByUrl(String url);

    Page<Product> findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(Pageable pageable);

    Slice<Product> findAllByReviewsParsedAndRatingIsNotNull(boolean reviewsParsed, Pageable pageable);

    Slice<Product> findAllByPriceHistoryParsed(boolean priceHistoryParsed, Pageable pageable);
}