package org.skroutz.scraper.skroutzwebscraper.search.domain.repository;

import org.skroutz.scraper.skroutzwebscraper.search.domain.entity.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductElasticsearchRepository extends ElasticsearchRepository<ProductDocument, Long> {
}
