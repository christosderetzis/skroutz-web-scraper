package org.skroutz.scraper.skroutzwebscraper.search.application.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.product.ProductSearchEvent;
import org.skroutz.scraper.skroutzwebscraper.search.domain.entity.ProductDocument;
import org.skroutz.scraper.skroutzwebscraper.search.domain.repository.ProductElasticsearchRepository;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.mapper.ProductDocumentMapper;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProductSearchHandler {

    private final ProductElasticsearchRepository productElasticsearchRepository;
    private final ProductDocumentMapper productDocumentMapper;

    @ApplicationModuleListener
    public void handleProductSearchEvent(ProductSearchEvent event) {
        List<ProductSearchEvent.ProductSearchEventItem> eventItems = event.items();
        try {
            List<ProductDocument> documents = eventItems.stream()
                    .map(productDocumentMapper::toDocument)
                    .toList();
            productElasticsearchRepository.saveAll(documents);
            log.info("Indexed {} products to Elasticsearch", eventItems.size());
        } catch (Exception e) {
            log.error("Failed to index products to Elasticsearch: {}", e.getMessage(), e);
        }
    }
}
