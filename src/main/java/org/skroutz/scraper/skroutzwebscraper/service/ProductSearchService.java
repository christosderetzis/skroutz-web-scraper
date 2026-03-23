package org.skroutz.scraper.skroutzwebscraper.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.document.ProductDocument;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.mapper.ProductDocumentMapper;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductElasticsearchRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final ProductElasticsearchRepository productElasticsearchRepository;
    private final ProductDocumentMapper productDocumentMapper;

    public Boolean indexProduct(Product product) {
        try {
            ProductDocument document = productDocumentMapper.toDocument(product);
            productElasticsearchRepository.save(document);
            log.info("Indexed product {} to Elasticsearch", product.getId());
            return true;
        } catch (Exception e) {
            log.error("Failed to index product {} to Elasticsearch: {}", product.getId(), e.getMessage(), e);
            return false;
        }
    }

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
