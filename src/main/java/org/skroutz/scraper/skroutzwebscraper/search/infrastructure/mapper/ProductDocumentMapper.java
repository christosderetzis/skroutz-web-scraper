package org.skroutz.scraper.skroutzwebscraper.search.infrastructure.mapper;

import org.mapstruct.Mapper;
import org.skroutz.scraper.skroutzwebscraper.product.ProductSearchEvent;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.ProductSuggestionDto;
import org.skroutz.scraper.skroutzwebscraper.search.domain.entity.ProductDocument;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.ProductItemDto;

@Mapper(componentModel = "spring")
public interface ProductDocumentMapper {

    ProductDocument toDocument(ProductSearchEvent.ProductSearchEventItem product);

    ProductSuggestionDto toSuggestionDto(ProductDocument productDocument);

    ProductItemDto toItemDto(ProductDocument productDocument);
}
