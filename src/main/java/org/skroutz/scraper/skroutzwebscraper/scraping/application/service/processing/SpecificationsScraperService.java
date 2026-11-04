package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.CategoryMappingSchema;
import org.skroutz.scraper.skroutzwebscraper.product.ProductDto;
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events.SpecificationsScrapeResult;
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

    public SpecificationsScrapeResult scrapeBatch(List<ProductDto> products, Map<String, CategoryMappingSchema> schemas) {
        List<SpecificationsScrapeResult.SpecificationsScrapeItem> results = new ArrayList<>();

        for (ProductDto product : products) {
            String url = product.url();
            if (url == null || url.isBlank()) {
                log.warn("Product URL is empty or null for product: {}", product.id());
                results.add(new SpecificationsScrapeResult.SpecificationsScrapeItem(product.id(), null, null, null, false));
                continue;
            }

            try {
                log.info("Parsing specifications for product: {}", product.id());
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
                    JsonNode normalizedSpecs = normalizeSpecs(product.id(), product.category(), rawSpecs, schemas);
                    results.add(new SpecificationsScrapeResult.SpecificationsScrapeItem(product.id(), rawSpecs, normalizedSpecs, brand, true));
                    log.info("Successfully parsed specifications for product ID: {}", product.id());
                } else {
                    log.warn("No specifications found for product: {}", product.id());
                    results.add(new SpecificationsScrapeResult.SpecificationsScrapeItem(product.id(), null, null, null, false));
                }

            } catch (Exception e) {
                log.error("Error parsing specifications for product {}: {}", product.id(), e.getMessage(), e);
                results.add(new SpecificationsScrapeResult.SpecificationsScrapeItem(product.id(), null, null, null, false));
            }
        }
        return new SpecificationsScrapeResult(results);
    }

    private JsonNode normalizeSpecs(Long productId, String category, JsonNode rawSpecs, Map<String, CategoryMappingSchema> schemas) {
        return Optional.ofNullable(schemas.get(category))
                .map(categorySchema -> {
                    try {
                        String json = specsNormalizerUtils.normalize(rawSpecs, categorySchema);
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
