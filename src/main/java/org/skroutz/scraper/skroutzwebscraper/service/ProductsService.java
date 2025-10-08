package org.skroutz.scraper.skroutzwebscraper.service;

import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.dto.ProductDetailsResponseDto;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.mapper.ProductMapper;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.scraper.ProductsScraper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductsService {

    private final ProductsScraper productsScraper;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Transactional
    public void scrapeAndSaveProducts(String url) {
        log.info("Starting to scrape and save products from URL: {}", url);

        try {
            List<Product> scrapedProducts = productsScraper.scrapeProducts(url);
            log.info("Scraped {} products from URL", scrapedProducts.size());

            List<Product> savedProducts = new ArrayList<>();

            for (Product product : scrapedProducts) {
                try {
                    Product savedProduct = saveProductIfNotExists(product);
                    if (savedProduct != null) {
                        savedProducts.add(savedProduct);
                    }
                } catch (Exception e) {
                    log.error("Error saving product '{}': {}", product.getTitle(), e.getMessage());
                }
            }

            log.info("Successfully saved {} new products to database", savedProducts.size());

        } catch (Exception e) {
            log.error("Error during scraping and saving process: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to scrape and save products", e);
        }
    }

    public Integer getNumberOfWebPages(String url) {
        log.info("Getting number of web pages for URL: {}", url);

        try {
            Integer numberOfPages = productsScraper.getNumberOfPages(url);
            log.info("Number of web pages found: {}", numberOfPages);
            return numberOfPages;
        } catch (Exception e) {
            log.error("Error getting number of web pages: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get number of web pages", e);
        }
    }

    private Product saveProductIfNotExists(Product product) {
        if (product.getUrl() == null || product.getTitle() == null) {
            log.warn("Skipping product with missing URL or title");
            return null;
        }

        Optional<Product> existingProduct = productRepository.findByUrl(product.getUrl());

        if (existingProduct.isPresent()) {
            log.debug("Product already exists with URL: {}", product.getUrl());
            return updateExistingProduct(existingProduct.get(), product);
        } else {
            Product savedProduct = productRepository.save(product);
            log.debug("Saved new product: {}", savedProduct.getTitle());
            return savedProduct;
        }
    }

    private Product updateExistingProduct(Product existing, Product scraped) {
        boolean updated = false;

        updated |= updateField(scraped.getPrice(), existing.getPrice(), existing::setPrice);
        updated |= updateField(scraped.getRating(), existing.getRating(), existing::setRating);
        updated |= updateField(scraped.getImageUrl(), existing.getImageUrl(), existing::setImageUrl);
        updated |= updateField(scraped.getDescription(), existing.getDescription(), existing::setDescription);

        if (updated) {
            Product updatedProduct = productRepository.save(existing);
            log.debug("Updated existing product: {}", updatedProduct.getTitle());
            return updatedProduct;
        }

        return existing;
    }

    private <T> boolean updateField(T newValue, T currentValue, Consumer<T> setter) {
        if (newValue != null && !newValue.equals(currentValue)) {
            setter.accept(newValue);
            return true;
        }
        return false;
    }

    public ProductDetailsResponseDto getProductDetails(Long id) {
        return productRepository.findById(id)
                .map(productMapper::toProductResponseDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found with id: " + id));
    }
}
