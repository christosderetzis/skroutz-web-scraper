package org.skroutz.scraper.skroutzwebscraper.scraper

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.springframework.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ProductsScraperSpec extends Specification {

    ApplicationContext applicationContext = Mock()

    @Subject
    ProductsScraper productsScraper = new ProductsScraper(applicationContext, "https://www.skroutz.gr")

    // ---------- PAGINATION ----------

    @Unroll
    def "parsePaginationInfo returns #expected for pagination text '#paginationText'"() {
        given:
        def html = """
            <div class="paginator">
                <button><span>${paginationText}</span></button>
            </div>
        """

        when:
        def result = productsScraper.parsePaginationInfo(html)

        then:
        result == expected

        where:
        paginationText      || expected
        "1 from 11"         || 11
        "2 from 5"          || 5
        "1 από 7"           || 7
        "Page 1 of 3"       || null
        "invalid text"      || null
        "1 from"            || null
    }

    def "parsePaginationInfo returns null when pagination element missing"() {
        given:
        def html= "<html></html>"

        when:
        def result = productsScraper.parsePaginationInfo(html)

        then:
        result == null
    }

    // ---------- URL + TITLE ----------

    def "extractTitle sets title when aTag is present"() {
        given:
            def html = """
                <div>
                    <a class="js-sku-link" href="http://example.com" title="Example Product"></a>
                </div>
            """
            Element productElement = Jsoup.parse(html).selectFirst("div")

        when:
            def result = productsScraper.extractTitle(productElement)

        then:
            result == "Example Product"
    }

    def "extractTitle does nothing if aTag is missing"() {
        given:
            Element productElement = Jsoup.parse("<div></div>").selectFirst("div")

        when:
            def result = productsScraper.extractTitle(productElement)

        then:
            result == null
    }

    def "extractUrl sets url when aTag is present"() {
        given:
            def html = """
                <div>
                    <a class="js-sku-link" href="${href}" title="Example Product"></a>
                </div>
            """
            Element productElement = Jsoup.parse(html).selectFirst("div")

        when:
            def result = productsScraper.extractUrl(productElement)

        then:
            result == expectedUrl

        where:
            href                                                          || expectedUrl
            "http://example.com"                                          || "http://example.com"
            "https://example.com"                                         || "https://example.com"
            "/s/45762495/Apple-iPhone-15-Pro-8-128GB-Black-Titanium.html" || "https://www.skroutz.gr/s/45762495/Apple-iPhone-15-Pro-8-128GB-Black-Titanium.html"
            "/c/40/kinhta-tilefwna.html"                                  || "https://www.skroutz.gr/c/40/kinhta-tilefwna.html"
    }



    def "extractUrl does nothing if aTag is missing"() {
        given:
            Element productElement = Jsoup.parse("<div></div>").selectFirst("div")

        when:
            def result = productsScraper.extractUrl(productElement)

        then:
            result == null
    }

    // ---------- PRICE ----------

    @Unroll
    def "extractPrice returns price=#expected for price text '#priceText'"() {
        given:
            def html = """
                <div>
                    <a data-e2e-testid="sku-price-link">${priceText}</a>
                </div>
            """
            Element productElement = Jsoup.parse(html).selectFirst("div")

        when:
            def result = productsScraper.extractPrice(productElement)

        then:
            result == expected

        where:
            priceText           || expected
            "500,00"            || new BigDecimal("500.00")
            "1.200,50"          || new BigDecimal("1200.50")
            "500,00 - 600,00"   || new BigDecimal("500.00")
            "from 700,00 €"     || new BigDecimal("700.00")
            "από 800,00 €"      || new BigDecimal("800.00")
    }

    def "extractPrice returns null if priceSpan missing"() {
        given:
        Element productElement = Jsoup.parse("<div></div>").selectFirst("div")

        when:
        def result = productsScraper.extractPrice(productElement)

        then:
        result == null
    }

    def "extractPrice returns null if price is not a number"() {
        given:
            def html = """
                <div>
                    <a data-e2e-testid="sku-price-link">not a price</a>
                </div>
            """
            Element productElement = Jsoup.parse(html).selectFirst("div")

        when:
            def result = productsScraper.extractPrice(productElement)

        then:




        result == null
    }

    // ---------- IMAGE ----------

    def "extractImageUrl sets imageUrl when img is present"() {
        given:
            def html = """
                <div>
                    <div class="image-container">
                        <img src="http://example.com/image.jpg"/>
                    </div>
                </div>
            """
            Element productElement = Jsoup.parse(html).selectFirst("div")

        when:
            def result = productsScraper.extractImageUrl(productElement)

        then:
            result == "http://example.com/image.jpg"
    }

    def "extractImageUrl sets imageUrl to null if img missing"() {
        given:
            Element productElement = Jsoup.parse("<div></div>").selectFirst("div")

        when:
            def result = productsScraper.extractImageUrl(productElement)

        then:
            result == null
    }

    def "extractImageUrl sets imageUrl to null if element does not exist" () {
        when:
            def result = productsScraper.extractImageUrl(null)

        then:
            result == null
    }

    // ---------- DESCRIPTION ----------

    def "extractDescription sets description when desc is present"() {
        given:
            def html = """
                <div>
                    <p class="specs">Product description</p>
                </div>
            """
            Element productElement = Jsoup.parse(html).selectFirst("div")

        when:
            def result = productsScraper.extractDescription(productElement)

        then:
            result == "Product description"
    }

    def "extractDescription sets description to null if desc missing"() {
        given:
            Element productElement = Jsoup.parse("<div></div>").selectFirst("div")

        when:
            def result = productsScraper.extractDescription(productElement)

        then:
            result == null
    }

    // ---------- RATING ----------

    @Unroll
    def "extractRating sets rating=#expected for rating text '#ratingText'"() {
        given:
            def html = """
                <div>
                    <div class="rating-wrapper">
                        <span data-testid="star-rating-value">${ratingText}</span>
                    </div>
                </div>
            """
            Element productElement = Jsoup.parse(html).selectFirst("div")

        when:
            def result = productsScraper.extractRating(productElement)

        then:
            result == expected

        where:
            ratingText   || expected
            "4,5"        || new BigDecimal("4.5")
            "3.0"        || new BigDecimal("3.0")
    }

    def "extractRating sets rating to null if rating missing"() {
        given:
            Element productElement = Jsoup.parse("<div></div>").selectFirst("div")

        when:
            def result = productsScraper.extractRating(productElement)

        then:
            result == null
    }

    def "extractRating sets rating to null if rating is not numeric"() {
        given:
            def html = """
                <div>
                    <div class="rating-wrapper">
                        <span data-testid="star-rating-value">not a rating</span>
                    </div>
                </div>
            """
            Element productElement = Jsoup.parse(html).selectFirst("div")

        when:
            def result = productsScraper.extractRating(productElement)

        then:
            result == null
    }
}