package org.skroutz.scraper.skroutzwebscraper.repository;

import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByUrl(String url);

    Page<Product> findAllBySpecificationsIsNullOrderByIdAsc(Pageable pageable);

    List<Product> findAllByReviewsParsedAndRatingIsNotNull(boolean reviewsParsed);

    List<Product> findAllByPriceHistoryParsed(boolean priceHistoryParsed);
}