package org.skroutz.scraper.skroutzwebscraper.priceHistory.application.controller;

import lombok.RequiredArgsConstructor;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.application.service.ProductBuyingAnalysisService;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.BuyAnalysis;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class PriceHistoryController {

    private final ProductBuyingAnalysisService buyingAnalysisService;

    @GetMapping("/{productId}/buy-analysis")
    public BuyAnalysis getBuyAnalysis(
            @PathVariable Long productId
    ) {

        return buyingAnalysisService.analyze(productId);
    }
}
