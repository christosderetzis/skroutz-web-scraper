package org.skroutz.scraper.skroutzwebscraper.priceHistory.application.controller;

import lombok.RequiredArgsConstructor;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.application.service.PriceHistoryService;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.BuyRecommendationResult;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.PriceTrendResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class PriceHistoryController {

    private final PriceHistoryService priceHistoryService;

    @GetMapping("/{id}/price-trend")
    public ResponseEntity<PriceTrendResult> getPriceTrend(@PathVariable Long id) {
        return ResponseEntity.ok(priceHistoryService.calculatePriceTrend(id));
    }

    @GetMapping("/{id}/buy-recommendation")
    public ResponseEntity<BuyRecommendationResult> getBuyRecommendation(@PathVariable Long id) {
        return ResponseEntity.ok(priceHistoryService.getBuyRecommendation(id));
    }
}
