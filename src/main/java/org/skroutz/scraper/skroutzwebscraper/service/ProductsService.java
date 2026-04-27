package org.skroutz.scraper.skroutzwebscraper.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.dto.ProductApiResponseDto;
import org.skroutz.scraper.skroutzwebscraper.dto.ProductDetailsResponseDto;
import org.skroutz.scraper.skroutzwebscraper.dto.ScraperRequestDto;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.mapper.ProductMapper;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.scraper.ProductsScraper;
import org.skroutz.scraper.skroutzwebscraper.utils.UrlBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Consumer;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductsService {

    private final ProductsScraper productsScraper;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final UrlBuilder urlBuilder;

    public void scrapeProducts(ScraperRequestDto request, boolean multiplePages) {
        log.info("Starting scrape for URL: {}, multiplePages: {}, category: {}",
                request.getUrl(),
                multiplePages,
                request.getCategory());

        if (multiplePages) {
            scrapeMultiplePages(request.getUrl(), request.getCategory());
        } else {
            scrapePage(request.getUrl(), request.getCategory());
        }
    }

    private void scrapeMultiplePages(String url, String category) {
        int totalPages = getTotalPages(url);

        if (totalPages <= 0) {
            log.warn("No pages found for URL: {}", url);
            return;
        }

        log.info("Found {} pages to scrape", totalPages);

        IntStream.rangeClosed(1, totalPages)
                .mapToObj(page -> urlBuilder.buildUrlWithPage(url, page))
                .forEach(pageUrl -> scrapePage(pageUrl, category));

        log.info("Finished scraping all pages.");
    }

    private void scrapePage(String url, String category) {
        try {
            log.info("Scraping page: {}", url);

            String jsonUrl = urlBuilder.convertToJsonUrl(url);

            ProductApiResponseDto response =
                    productsScraper.fetchProductsPage(jsonUrl);

            response.getItems().stream()
                    .map(dto -> productMapper.toProduct(dto, category, urlBuilder))
                    .forEach(this::saveOrUpdate);

            log.info("Finished scraping page: {}", url);

        } catch (Exception e) {
            log.error("Failed scraping page {}: {}", url, e.getMessage(), e);
        }
    }

    private int getTotalPages(String url) {
        String jsonUrl = urlBuilder.convertToJsonUrl(url);

        ProductApiResponseDto response =
                productsScraper.fetchProductsPage(jsonUrl);

        return response.getPage().getTotalPages();
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
            Product updatedProduct = productRepository.save(existing);
            log.debug("Updated existing product: {}", updatedProduct.getTitle());
        }
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
