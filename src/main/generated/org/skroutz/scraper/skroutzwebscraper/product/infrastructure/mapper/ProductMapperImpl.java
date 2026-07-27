package org.skroutz.scraper.skroutzwebscraper.product.infrastructure.mapper;

import javax.annotation.processing.Generated;
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.product.infrastructure.dto.ProductDetailsResponseDto;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.api.ProductApiResponseDto;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.utils.UrlBuilder;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-08T17:10:54+0300",
    comments = "version: 1.6.3, compiler: javac, environment: Java 25.0.2 (Eclipse Adoptium)"
)
@Component
public class ProductMapperImpl implements ProductMapper {

    @Override
    public ProductDetailsResponseDto toProductResponseDto(Product product) {
        if ( product == null ) {
            return null;
        }

        ProductDetailsResponseDto.ProductDetailsResponseDtoBuilder productDetailsResponseDto = ProductDetailsResponseDto.builder();

        productDetailsResponseDto.specifications( mapJsonNodeToString( product.getSpecifications() ) );
        productDetailsResponseDto.id( product.getId() );
        productDetailsResponseDto.url( product.getUrl() );
        productDetailsResponseDto.title( product.getTitle() );
        productDetailsResponseDto.imageUrl( product.getImageUrl() );
        productDetailsResponseDto.description( product.getDescription() );
        productDetailsResponseDto.price( product.getPrice() );
        productDetailsResponseDto.rating( product.getRating() );

        return productDetailsResponseDto.build();
    }

    @Override
    public Product toProduct(ProductApiResponseDto.ProductDetailsResponseDto dto, String category, UrlBuilder urlBuilder) {
        if ( dto == null && category == null && urlBuilder == null ) {
            return null;
        }

        Product.ProductBuilder product = Product.builder();

        if ( dto != null ) {
            product.price( stringToPrice( dto.getPrice() ) );
            product.rating( stringToBigDecimal( dto.getRating() ) );
            product.title( dto.getTitle() );
            product.imageUrl( dto.getImageUrl() );
            product.description( dto.getDescription() );
        }
        product.category( category );
        product.url( urlBuilder.buildFullProductUrl(dto.getUrl()) );

        return product.build();
    }
}
