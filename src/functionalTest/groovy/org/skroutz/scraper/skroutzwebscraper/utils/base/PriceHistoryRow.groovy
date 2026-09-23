package org.skroutz.scraper.skroutzwebscraper.utils.base

class PriceHistoryRow {

    final int daysAgo
    final BigDecimal price

    PriceHistoryRow(int daysAgo, BigDecimal price) {
        this.daysAgo = daysAgo
        this.price = price
    }

    static PriceHistoryRow row(int daysAgo, BigDecimal price) {
        new PriceHistoryRow(daysAgo, price)
    }
}
