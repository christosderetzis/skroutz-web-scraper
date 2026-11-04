package org.skroutz.scraper.skroutzwebscraper.product;

import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.product.domain.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.product.infrastructure.exception.ProductNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

@Component
public class ProductFacade {

    private final ProductRepository productRepository;

    public ProductFacade(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Slice<ProductDto> getUnparsedReviewsProducts(int page, int size) {
        return productRepository.findAllByReviewsParsedAndRatingIsNotNull(false, PageRequest.of(page, size))
                .map(this::mapToProductDto);
    }

    public Slice<ProductDto> getUnparsedPriceHistoryProducts(int page, int size) {
        return productRepository.findAllByPriceHistoryParsed(false, PageRequest.of(page, size))
                .map(this::mapToProductDto);
    }

    public Slice<ProductDto> getUnparsedSpecificationsProducts(int page, int size) {
        return productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(PageRequest.of(page, size))
                .map(this::mapToProductDto);
    }

    public ProductDto getProductById(Long productId) {
        return productRepository.findById(productId)
                .map(this::mapToProductDto)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    public boolean existsById(Long productId) {
        return productRepository.existsById(productId);
    }

    private ProductDto mapToProductDto(Product product) {
        return new ProductDto(product.getId(), product.getUrl(), product.getTitle(), product.getDescription(), product.getCategory());
    }
}
