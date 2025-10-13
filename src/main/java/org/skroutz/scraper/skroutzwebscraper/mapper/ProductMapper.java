package org.skroutz.scraper.skroutzwebscraper.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.skroutz.scraper.skroutzwebscraper.dto.ProductDetailsResponseDto;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductDetailsResponseDto toProductResponseDto(Product product);
}
