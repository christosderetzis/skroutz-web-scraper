package org.skroutz.scraper.skroutzwebscraper.utils.creators

import com.github.javafaker.Faker
import org.skroutz.scraper.skroutzwebscraper.entity.Product

class ProductCreator {

    static Faker faker = new Faker(Locale.UK)

    static Product createRandomProduct() {
        Product.builder()
                .title(faker.commerce().productName())
                .price(faker.number().randomDouble(2, 1, 1000).toBigDecimal())
                .imageUrl(faker.internet().url())
                .url(faker.internet().url())
                .description(faker.lorem().paragraph())
                .rating(faker.number().randomDouble(2, 1, 5).toBigDecimal())
                .reviewsParsed(false)
                .priceHistoryParsed(false)
                .build()
    }
}
