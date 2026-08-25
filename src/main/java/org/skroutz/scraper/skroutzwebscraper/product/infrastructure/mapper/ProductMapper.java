package org.skroutz.scraper.skroutzwebscraper.product.infrastructure.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.product.infrastructure.dto.ProductDetailsResponseDto;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.api.ProductApiResponseDto;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.utils.UrlBuilder;

import java.math.BigDecimal;
import java.util.Map;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "specifications", source="specifications", qualifiedByName = "jsonNodeToMap")
    ProductDetailsResponseDto toProductResponseDto(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "specifications", ignore = true)
    @Mapping(target = "specificationsSkipped", ignore = true)
    @Mapping(target = "reviewsParsed", ignore = true)
    @Mapping(target = "priceHistoryParsed", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "url", expression = "java(urlBuilder.buildFullProductUrl(dto.getUrl()))")
    @Mapping(target = "price", source = "dto.price", qualifiedByName = "stringToPrice")
    @Mapping(target = "rating", source = "dto.rating", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "imageUrl", source = "dto.imageUrl")
    Product toProduct(ProductApiResponseDto.ProductDetailsResponseDto dto, String category, UrlBuilder urlBuilder);

    @Named("stringToPrice")
    default BigDecimal stringToPrice(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }

        try {
            String cleanValue = value
                    .replaceAll("[€$£¥\\s]", "")    // Remove currency symbols and spaces
                    .replaceAll("\\.", "")          // Remove thousands separator (European format)
                    .replace(",", ".");             // Convert decimal separator

            return new BigDecimal(cleanValue);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric string: " + value, e);
        }
    }

    @Named("stringToBigDecimal")
    default BigDecimal stringToBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            String normalized = value.trim().replace(",", ".");
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid rating string: " + value, e);
        }
    }

    @Named("jsonNodeToMap")
    default Map<String, Object> jsonNodeToMap(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return null;
        }
        // Using Jackson's built-in conversion
        return new ObjectMapper().convertValue(jsonNode, new TypeReference<Map<String, Object>>() {});
    }
}
