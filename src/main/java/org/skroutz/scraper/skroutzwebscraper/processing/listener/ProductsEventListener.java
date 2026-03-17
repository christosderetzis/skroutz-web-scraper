package org.skroutz.scraper.skroutzwebscraper.processing.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.processing.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.processing.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.scraping.event.ProductsScrapedEvent;
import org.skroutz.scraper.skroutzwebscraper.scraping.dto.ScrapedProductData;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductsEventListener {

    private final ProductRepository productRepository;

    @EventListener
    @Transactional
    public void handleProductsScraped(ProductsScrapedEvent event) {
        log.info("Received ProductsScrapedEvent with {} products", event.products().size());

        List<Product> savedProducts = new ArrayList<>();

        for (ScrapedProductData data : event.products()) {
            try {
                Product product = Product.builder()
                        .title(data.title())
                        .url(data.url())
                        .price(data.price())
                        .description(data.description())
                        .rating(data.rating())
                        .imageUrl(data.imageUrl())
                        .build();

                Product savedProduct = saveProductIfNotExists(product);
                if (savedProduct != null) {
                    savedProducts.add(savedProduct);
                }
            } catch (Exception e) {
                log.error("Error saving product '{}': {}", data.title(), e.getMessage());
            }
        }

        log.info("Successfully saved {} new products to database", savedProducts.size());
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
}
