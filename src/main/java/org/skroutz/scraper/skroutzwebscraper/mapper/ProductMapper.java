package org.skroutz.scraper.skroutzwebscraper.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.skroutz.scraper.skroutzwebscraper.dto.ProductApiResponseDto;
import org.skroutz.scraper.skroutzwebscraper.dto.ProductDetailsResponseDto;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.utils.UrlBuilder;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface ProductMapper {

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
}
