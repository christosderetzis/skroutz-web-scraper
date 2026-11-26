package org.skroutz.scraper.skroutzwebscraper.product.infrastructure.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.skroutz.scraper.skroutzwebscraper.product.ProductSearchEvent;
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.product.infrastructure.dto.ProductDetailsResponseDto;
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.api.ProductApiResponseDto;
import org.skroutz.scraper.skroutzwebscraper.common.utils.UrlBuilder;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "specifications", source="specifications", qualifiedByName = "jsonNodeToMap")
    ProductDetailsResponseDto toProductResponseDto(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "specifications", ignore = true)
    @Mapping(target = "specificationsSkipped", ignore = true)
    @Mapping(target = "numberOfReviews", source = "dto.ratingCount", qualifiedByName = "stringToInteger")
    @Mapping(target = "reviewsParsed", expression = "java(stringToInteger(dto.getRatingCount()) == 0)")
    @Mapping(target = "priceHistoryParsed", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "url", expression = "java(urlBuilder.buildFullProductUrl(dto.getUrl()))")
    @Mapping(target = "price", source = "dto.price", qualifiedByName = "stringToPrice")
    @Mapping(target = "rating", source = "dto.rating", qualifiedByName = "stringToBigDecimal")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "imageUrl", source = "dto.imageUrl")
    Product toProduct(ProductApiResponseDto.ProductDetailsResponseDto dto, String category, UrlBuilder urlBuilder);

    @Mapping(target = "specifications", source = "elasticSearchSpecifications", qualifiedByName = "jsonNodeToMap")
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "timestampToInstant")
    @Mapping(target = "updatedAt", source = "updatedAt", qualifiedByName = "timestampToInstant")
    ProductSearchEvent.ProductSearchEventItem toProductSearchEventItem(Product product);

    @Named("timestampToInstant")
    default Instant map(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    // Custom mapping method for price that picks the minimum value if multiple prices are present in the string
    @Named("stringToPrice")
    default BigDecimal stringToPrice(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }

        Matcher matcher = Pattern.compile("\\d+(?:[.,]\\d+)*").matcher(value);

        if (!matcher.find()) {
            throw new IllegalArgumentException("No numeric value found in: " + value);
        }

        BigDecimal first = toBigDecimal(matcher.group());

        if (!matcher.find()) {
            return first;
        }

        BigDecimal second = toBigDecimal(matcher.group());

        return first.min(second);
    }

    default BigDecimal toBigDecimal(String number) {
        String cleanValue = number
                .replace(".", "")
                .replace(",", ".");

        return new BigDecimal(cleanValue);
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

    @Named("stringToInteger")
    default Integer stringToInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(digits);
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
