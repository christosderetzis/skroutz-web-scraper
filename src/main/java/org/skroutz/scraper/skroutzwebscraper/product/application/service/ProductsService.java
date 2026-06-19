package org.skroutz.scraper.skroutzwebscraper.product.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.product.domain.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.product.infrastructure.dto.ProductDetailsResponseDto;
import org.skroutz.scraper.skroutzwebscraper.product.infrastructure.mapper.ProductMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductsService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductDetailsResponseDto getProductDetails(Long id) {
        return productRepository.findById(id)
                .map(productMapper::toProductResponseDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found with id: " + id));
    }
}
