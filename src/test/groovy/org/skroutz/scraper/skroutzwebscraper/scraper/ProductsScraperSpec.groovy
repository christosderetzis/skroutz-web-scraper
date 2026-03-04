package org.skroutz.scraper.skroutzwebscraper.scraper

import org.openqa.selenium.*
import org.skroutz.scraper.skroutzwebscraper.entity.Product
import org.springframework.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ProductsScraperSpec extends Specification {

    ApplicationContext applicationContext = Mock()

    @Subject
    ProductsScraper productsScraper = new ProductsScraper(applicationContext, "https://www.skroutz.gr")

    @Unroll
    def "parsePaginationInfo returns #expected for pagination text '#paginationText'"() {
        given: "a WebDriver with a pagination span"
            WebDriver webDriver = Mock()
            webDriver.findElement(By.cssSelector(HtmlFields.PAGINATION_BUTTON)) >> Mock(WebElement) {
                getText() >> paginationText
            }

        when: "parsing pagination info"
            def result = productsScraper.parsePaginationInfo(webDriver)

        then: "the correct number of pages is returned"
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

    def "parsePaginationInfo returns null when findElement throws"() {
        given: "a WebDriver that throws an exception"
            WebDriver webDriver = Mock()
            webDriver.findElement(By.cssSelector(HtmlFields.PAGINATION_BUTTON)) >> { throw new RuntimeException("not found") }

        when: "parsing pagination info"
            def result = productsScraper.parsePaginationInfo(webDriver)

        then: "null is returned"
            result == null
    }

    def "parsePaginationInfo returns null when totalPagesText is not a number"() {
        given: "a WebDriver with non-numeric total pages"
            WebDriver webDriver = Mock()
            webDriver.findElement(By.cssSelector(HtmlFields.PAGINATION_BUTTON)) >> Mock(WebElement) {
                getText() >> "1 from abc"
            }

        when: "parsing pagination info"
            def result = productsScraper.parsePaginationInfo(webDriver)

        then: "null is returned"
            result == null
    }

    @Unroll
    def "extractUrlAndTitle sets url=#expectedUrl and title when aTag has href='#href'"() {
        given: "a productElement with a valid aTag"
            Product product = new Product()
            WebElement productElement = Mock()
            productElement.findElement(By.cssSelector(HtmlFields.PRODUCT_LINK)) >> Mock(WebElement) {
                getAttribute("href") >> href
                getAttribute("title") >> "Example Product"
            }

        when: "extracting url and title"
            productsScraper.extractUrlAndTitle(productElement, product)

        then: "url and title are set"
            product.url == expectedUrl
            product.title == "Example Product"

        where:
            href                                                          || expectedUrl
            "http://example.com"                                          || "http://example.com"
            "https://example.com"                                         || "https://example.com"
            "/s/45762495/Apple-iPhone-15-Pro-8-128GB-Black-Titanium.html" || "https://www.skroutz.gr/s/45762495/Apple-iPhone-15-Pro-8-128GB-Black-Titanium.html"
            "/c/40/kinhta-tilefwna.html"                                  || "https://www.skroutz.gr/c/40/kinhta-tilefwna.html"
    }

    def "extractUrlAndTitle does nothing if aTag is missing"() {
        given: "a productElement without aTag"
            Product product = new Product()
            WebElement productElement = Mock()
            productElement.findElement(By.cssSelector(HtmlFields.PRODUCT_LINK)) >> { throw new NoSuchElementException("not found") }

        when: "extracting url and title"
            productsScraper.extractUrlAndTitle(productElement, product)

        then: "url and title remain null"
            product.url == null
            product.title == null
    }

    @Unroll
    def "extractPrice sets price=#expected for price text '#priceText'"() {
        given: "a productElement with a priceSpan"
            Product product = new Product()
            WebElement priceSpan = Mock() {
                getText() >> priceText
            }
            WebElement productElement = Mock()
            productElement.findElement(By.cssSelector(HtmlFields.PRICE_LINK)) >> priceSpan

        when: "extracting price"
            productsScraper.extractPrice(productElement, product)

        then: "price is set as expected"
            product.price == expected

        where:
            priceText           || expected
            "500,00"            || new BigDecimal("500.00")
            "1.200,50"          || new BigDecimal("1200.50")
            "500,00 - 600,00"   || new BigDecimal("500.00")
            "from 700,00 €"     || new BigDecimal("700.00")
            "από 800,00 €"      || new BigDecimal("800.00")
    }

    def "extractPrice sets price to null if priceSpan is missing"() {
        given: "a productElement without priceSpan"
            Product product = new Product()
            WebElement productElement = Mock()
            productElement.findElement(By.cssSelector(HtmlFields.PRICE_LINK)) >> { throw new NoSuchElementException("not found") }

        when: "extracting price"
            productsScraper.extractPrice(productElement, product)

        then: "price is null"
            product.price == null
    }

    def "extractPrice sets price to null if price is not a number"() {
        given: "a productElement with non-numeric price"
            Product product = new Product()
            WebElement priceSpan = Mock() {
                getText() >> "not a price"
            }
            WebElement productElement = Mock()
            productElement.findElement(By.cssSelector(HtmlFields.PRICE_LINK)) >> priceSpan

        when: "extracting price"
            productsScraper.extractPrice(productElement, product)

        then: "price is null"
            product.price == null
    }

    @Unroll
    def "processPrice returns '#expected' for input '#input'"() {
        given: "a priceSpan mock"
            def priceSpan = Mock(WebElement) {
                getText() >> input
            }
            def method = ProductsScraper.declaredMethods.find { it.name == "processPrice" }
            method.accessible = true

        when: "calling processPrice via reflection"
            def result = method.invoke(null, priceSpan)

        then: "the result is as expected"
            result == expected

        where:
            input                || expected
            "500,00"             || "500.00"
            "1.200,50"           || "1200.50"
            "500,00 - 600,00"    || "500.00"
            "from 700,00 €"      || "700.00"
            "από 800,00 €"       || "800.00"
    }

    def "extractImageUrl sets imageUrl when img is present"() {
        given: "a productElement with an img"
            Product product = new Product()
            WebElement img = Mock() {
                getAttribute("src") >> "http://example.com/image.jpg"
            }
            WebElement productElement = Mock()
            productElement.findElement(By.cssSelector(HtmlFields.IMAGE_CONTAINER)) >> img

        when: "extracting image url"
            productsScraper.extractImageUrl(productElement, product)

        then: "imageUrl is set"
            product.imageUrl == "http://example.com/image.jpg"
    }

    def "extractImageUrl sets imageUrl to null if img is missing"() {
        given: "a productElement without img"
            Product product = new Product()
            WebElement productElement = Mock()
            productElement.findElement(By.cssSelector(HtmlFields.IMAGE_CONTAINER)) >> { throw new NoSuchElementException("not found") }

        when: "extracting image url"
            productsScraper.extractImageUrl(productElement, product)

        then: "imageUrl is null"
            product.imageUrl == null
    }

    def "extractDescription sets description when desc is present and displayed"() {
        given: "a productElement with a displayed desc"
            Product product = new Product()
            WebElement desc = Mock() {
                isDisplayed() >> true
                getText() >> "Product description"
            }
            WebElement productElement = Mock()
            productElement.findElement(By.cssSelector(HtmlFields.DESCRIPTION)) >> desc

        when: "extracting description"
            productsScraper.extractDescription(productElement, product)

        then: "description is set"
            product.description == "Product description"
    }

    def "extractDescription sets description to null if desc is not displayed"() {
        given: "a productElement with a hidden desc"
            Product product = new Product()
            WebElement productElement = Mock()
            productElement.findElement(By.cssSelector(HtmlFields.DESCRIPTION)) >> Mock(WebElement) {
                isDisplayed() >> false
            }

        when: "extracting description"
            productsScraper.extractDescription(productElement, product)

        then: "description is null"
            product.description == null
    }

    def "extractDescription sets description to null if desc is missing"() {
        given: "a productElement without desc"
            Product product = new Product()
            WebElement productElement = Mock()
            productElement.findElement(By.cssSelector(HtmlFields.DESCRIPTION)) >> { throw new NoSuchElementException("not found") }

        when: "extracting description"
            productsScraper.extractDescription(productElement, product)

        then: "description is null"
            product.description == null
    }

    @Unroll
    def "extractRating sets rating=#expected for rating text '#ratingText'"() {
        given: "a productElement with a ratingSpan"
            Product product = new Product()
            WebElement ratingSpan = Mock() {
                getText() >> ratingText
            }
            WebElement productElement = Mock()
            productElement.findElement(By.cssSelector(HtmlFields.RATING)) >> ratingSpan

        when: "extracting rating"
            productsScraper.extractRating(productElement, product)

        then: "rating is set as expected"
            product.rating == expected

        where:
            ratingText   || expected
            "4,5"        || new BigDecimal("4.5")
            "3.0"        || new BigDecimal("3.0")
    }

    def "extractRating sets rating to null if ratingSpan is missing"() {
        given: "a productElement without ratingSpan"
            Product product = new Product()
            WebElement productElement = Mock()
            productElement.findElement(By.cssSelector(HtmlFields.RATING)) >> { throw new NoSuchElementException("not found") }

        when: "extracting rating"
            productsScraper.extractRating(productElement, product)

        then: "rating is null"
            product.rating == null
    }

    def "extractRating sets rating to null if rating is not a number"() {
        given: "a productElement with non-numeric rating"
            Product product = new Product()
            WebElement ratingSpan = Mock() {
                getText() >> "not a rating"
            }
            WebElement productElement = Mock()
            productElement.findElement(By.cssSelector(HtmlFields.RATING)) >> ratingSpan

        when: "extracting rating"
            productsScraper.extractRating(productElement, product)

        then: "rating is null"
            product.rating == null
    }
}
