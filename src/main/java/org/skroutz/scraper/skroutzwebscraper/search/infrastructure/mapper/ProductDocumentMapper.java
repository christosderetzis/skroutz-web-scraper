package org.skroutz.scraper.skroutzwebscraper.search.infrastructure.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.ProductSuggestionDto;
import org.skroutz.scraper.skroutzwebscraper.search.domain.entity.ProductDocument;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.ProductItemDto;
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

@Mapper(componentModel = "spring")
public interface ProductDocumentMapper {

    @Mapping(target = "specifications", source = "elasticSearchSpecifications", qualifiedByName = "jsonNodeToMap")
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "timestampToInstant")
    @Mapping(target = "updatedAt", source = "updatedAt", qualifiedByName = "timestampToInstant")
    ProductDocument toDocument(Product product);

    @Named("jsonNodeToMap")
    @SuppressWarnings("unchecked")
    default Map<String, Object> jsonNodeToMap(JsonNode jsonNode) {
        if (jsonNode == null) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(jsonNode, Map.class);
    }

    @Named("timestampToInstant")
    default Instant map(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    ProductSuggestionDto toSuggestionDto(ProductDocument productDocument);

    ProductItemDto toItemDto(ProductDocument productDocument);
}
