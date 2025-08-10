package org.skroutz.scraper.skroutzwebscraper.repository;

import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByUrl(String url);

    List<Product> findAllBySpecificationsParsed(boolean specificationsParsed);

    List<Product> findAllByReviewsParsed(boolean reviewsParsed);
}