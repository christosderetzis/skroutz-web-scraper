package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.events.SpecificationsScrapeResult;
import org.skroutz.scraper.skroutzwebscraper.category.domain.entity.CategorySchema;
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.scraper.SpecificationsScraper;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.utils.SpecificationsNormalizerUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecificationsScraperService {

    private final SpecificationsScraper specificationsScraper;
    private final SpecificationsNormalizerUtils specsNormalizerUtils;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${scraper.delays.specifications-ms:500}")
    private long specificationsDelayMs;

    public List<SpecificationsScrapeResult> scrapeBatch(List<Product> products, Map<String, CategorySchema> schemas) {
        List<SpecificationsScrapeResult> results = new ArrayList<>();

        for (Product product : products) {
            String url = product.getUrl();
            if (url == null || url.isBlank()) {
                log.warn("Product URL is empty or null for product: {}", product.getId());
                results.add(new SpecificationsScrapeResult(product.getId(), null, null, null, false));
                continue;
            }

            try {
                log.info("Parsing specifications for product: {}", product.getId());
                String formattedUrl = url.contains("?") ? url + "&lang=en" : url + "?lang=en";

                // Throttling requests outside database transactional contexts
                Thread.sleep(specificationsDelayMs);

                Optional<JsonNode> rawSpecsOpt = specificationsScraper.scrapeSpecifications(formattedUrl)
                        .filter(specifications -> !specifications.isEmpty());

                if (rawSpecsOpt.isPresent()) {
                    String brand = rawSpecsOpt.get().path("brand").asText(null);
                    JsonNode rawSpecs = rawSpecsOpt.get();
                    // remove brand from raw specs
                    if (brand != null) {
                        ((ObjectNode) rawSpecs).remove("brand");
                    }
                    JsonNode normalizedSpecs = normalizeSpecs(product.getId(), product.getCategory(), rawSpecs, schemas);
                    results.add(new SpecificationsScrapeResult(product.getId(), rawSpecs, normalizedSpecs, brand, true));
                    log.info("Successfully parsed specifications for product ID: {}", product.getId());
                } else {
                    log.warn("No specifications found for product: {}", product.getId());
                    results.add(new SpecificationsScrapeResult(product.getId(), null, null, null, false));
                }

            } catch (Exception e) {
                log.error("Error parsing specifications for product {}: {}", product.getId(), e.getMessage(), e);
                results.add(new SpecificationsScrapeResult(product.getId(), null, null, null, false));
            }
        }
        return results;
    }

    private JsonNode normalizeSpecs(Long productId, String category, JsonNode rawSpecs, Map<String, CategorySchema> schemas) {
        return Optional.ofNullable(schemas.get(category))
                .map(categorySchema -> {
                    try {
                        String json = specsNormalizerUtils.normalize(rawSpecs, categorySchema.getSchema());
                        return objectMapper.readTree(json);
                    } catch (Exception e) {
                        log.warn("Normalization failed for product {}", productId);
                        return null;
                    }
                })
                .orElseGet(() -> {
                    log.warn("No schema found for category '{}'", category);
                    return null;
                });
    }
}
