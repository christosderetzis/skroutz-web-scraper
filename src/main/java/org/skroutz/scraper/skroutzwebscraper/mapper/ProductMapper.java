package org.skroutz.scraper.skroutzwebscraper.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.skroutz.scraper.skroutzwebscraper.dto.ProductDetailsResponseDto;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(source = "id", target = "id")
    ProductDetailsResponseDto toProductResponseDto(Product product);
}
