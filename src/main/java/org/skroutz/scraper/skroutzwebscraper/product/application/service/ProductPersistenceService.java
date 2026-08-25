package org.skroutz.scraper.skroutzwebscraper.product.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.skroutz.scraper.skroutzwebscraper.product.domain.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.product.infrastructure.mapper.ProductMapper;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.events.SpecificationsScrapeResult;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.api.ProductApiResponseDto;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.utils.UrlBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductPersistenceService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final UrlBuilder urlBuilder;

    @Transactional // Good practice to wrap processing a batch of scraped items in a transaction
    public void saveOrUpdateProducts(ProductApiResponseDto response, String category) {
        response.getItems().stream()
                .map(dto -> productMapper.toProduct(dto, category, urlBuilder))
                .forEach(this::saveOrUpdate);
    }

    @Transactional
    public List<Product> saveSpecificationsResults(List<SpecificationsScrapeResult> results) {
        List<Product> productsToSave = new ArrayList<>();
        List<Product> productsToSync = new ArrayList<>();

        for (SpecificationsScrapeResult result : results) {
            productRepository.findById(result.productId()).ifPresent(product -> {
                if (result.isSuccess()) {
                    product.setSpecifications(result.rawSpecs());
                    product.setElasticSearchSpecifications(result.normalizedSpecs());
                    product.setBrand(result.brand());
                    product.setSpecificationsSkipped(false);
                    productsToSync.add(product);
                } else {
                    product.setSpecificationsSkipped(true);
                }
                productsToSave.add(product);
            });
        }

        if (!productsToSave.isEmpty()) {
            productRepository.saveAll(productsToSave);
            log.info("Saved {} product status updates to database", productsToSave.size());
        }

        // Returns only the items successfully updated to allow tracking downstream
        return productsToSync;
    }

    private void saveOrUpdate(Product scrapedProduct) {
        if (scrapedProduct.getUrl() == null || scrapedProduct.getTitle() == null) {
            log.warn("Skipping invalid product due to missing title/url");
            return;
        }

        productRepository.findByUrl(scrapedProduct.getUrl())
                .ifPresentOrElse(
                        existing -> updateExistingProduct(existing, scrapedProduct),
                        () -> saveNew(scrapedProduct)
                );
    }

    private void saveNew(Product product) {
        productRepository.save(product);
        log.debug("Saved new product: {}", product.getTitle());
    }

    private void updateExistingProduct(Product existing, Product scraped) {
        boolean updated = false;

        updated |= updateField(scraped.getPrice(), existing.getPrice(), existing::setPrice);
        updated |= updateField(scraped.getRating(), existing.getRating(), existing::setRating);
        updated |= updateField(scraped.getImageUrl(), existing.getImageUrl(), existing::setImageUrl);
        updated |= updateField(scraped.getDescription(), existing.getDescription(), existing::setDescription);
        updated |= updateField(scraped.getCategory(), existing.getCategory(), existing::setCategory);

        if (updated) {
            productRepository.save(existing); // explicitly save or let Spring Data dirty-checking handle it
            log.debug("Updated existing product: {}", existing.getTitle());
        }
    }

    private <T> boolean updateField(T newValue, T currentValue, Consumer<T> setter) {
        if (newValue != null && !newValue.equals(currentValue)) {
            setter.accept(newValue);
            return true;
        }
        return false;
    }
}
