package org.skroutz.scraper.skroutzwebscraper.search.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.search.domain.entity.ProductDocument;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.mapper.ProductDocumentMapper;
import org.skroutz.scraper.skroutzwebscraper.search.domain.repository.ProductElasticsearchRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexingService {

    private final ProductElasticsearchRepository productElasticsearchRepository;
    private final ProductDocumentMapper productDocumentMapper;

    public void indexProducts(List<Product> products) {
        try {
            List<ProductDocument> documents = products.stream()
                    .map(productDocumentMapper::toDocument)
                    .toList();
            productElasticsearchRepository.saveAll(documents);
            log.info("Indexed {} products to Elasticsearch", products.size());
        } catch (Exception e) {
            log.error("Failed to index products to Elasticsearch: {}", e.getMessage(), e);
        }
    }
}
