package org.skroutz.scraper.skroutzwebscraper.processing.mapper;

import org.mapstruct.Mapper;
import org.skroutz.scraper.skroutzwebscraper.processing.dto.ProductDetailsResponseDto;
import org.skroutz.scraper.skroutzwebscraper.processing.entity.Product;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductDetailsResponseDto toProductResponseDto(Product product);
}
