package org.skroutz.scraper.skroutzwebscraper.utils


import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class UrlBuilderSpec extends Specification {

    private static final String BASE_URL = "https://www.skroutz.gr"

    @Subject
    UrlBuilder urlBuilder = new UrlBuilder(BASE_URL)

    // convertToJsonUrl tests

    @Unroll
    def "convertToJsonUrl converts '#url' to '#expected'"() {
        when: "converting to JSON URL"
            def result = urlBuilder.convertToJsonUrl(url)

        then: "the correct URL is returned"
            result == expected

        where:
            url                                              || expected
            "https://www.skroutz.gr/products/123.html"       || "https://www.skroutz.gr/products/123.json"
            "https://www.skroutz.gr/c/1234.html"             || "https://www.skroutz.gr/c/1234.json"
            "https://www.skroutz.gr/page.html?param=value"   || "https://www.skroutz.gr/page.json?param=value"
            "https://www.skroutz.gr/products/123.json"       || "https://www.skroutz.gr/products/123.json"
            "https://www.skroutz.gr/products/123"            || "https://www.skroutz.gr/products/123"
            ""                                               || ""
    }

    def "convertToJsonUrl throws exception when URL is null"() {
        when: "converting null URL"
            urlBuilder.convertToJsonUrl(null)

        then: "IllegalArgumentException is thrown"
            def exception = thrown(IllegalArgumentException)
            exception.message == "URL cannot be null"
    }

    // buildUrlWithPage tests
    def "buildUrlWithPage returns base URL for page 1"() {
        given: "page number 1"
            def baseUrl = "https://www.skroutz.gr/c/40"
            def page = 1

        when: "building URL with page"
            def result = urlBuilder.buildUrlWithPage(baseUrl, page)

        then: "base URL is returned without page parameter"
            result == baseUrl
    }

    @Unroll
    def "buildUrlWithPage adds page parameter for page #page"() {
        given: "a base URL without query parameters"
            def baseUrl = "https://www.skroutz.gr/c/40"

        when: "building URL with page"
            def result = urlBuilder.buildUrlWithPage(baseUrl, page)

        then: "page parameter is appended"
            result == "https://www.skroutz.gr/c/40?page=$page"

        where:
            page << [2, 3, 10, 100]
    }

    def "buildUrlWithPage uses ampersand when URL already has query parameters"() {
        given: "a base URL with existing query parameters"
            def baseUrl = "https://www.skroutz.gr/c/40?filter=true"
            def page = 2

        when: "building URL with page"
            def result = urlBuilder.buildUrlWithPage(baseUrl, page)

        then: "page parameter is appended with ampersand"
            result == "https://www.skroutz.gr/c/40?filter=true&page=2"
    }

    @Unroll
    def "buildUrlWithPage throws exception for invalid page number #page"() {
        given: "a base URL"
            def baseUrl = "https://www.skroutz.gr/c/40"

        when: "building URL with invalid page number"
            urlBuilder.buildUrlWithPage(baseUrl, page)

        then: "IllegalArgumentException is thrown"
            def exception = thrown(IllegalArgumentException)
            exception.message == "Page number must be positive"

        where:
            page << [0, -1, -10, -100]
    }

    // buildFullProductUrl tests
    @Unroll
    def "buildFullProductUrl handles partial URL '#partialUrl'"() {
        when: "building full URL"
            def result = urlBuilder.buildFullProductUrl(partialUrl)

        then: "full URL is constructed correctly"
            result == expected

        where:
            partialUrl                           || expected
            "products/123/item.html"             || "https://www.skroutz.gr/products/123/item.html"
            "/products/456/item.html"            || "https://www.skroutz.gr/products/456/item.html"
            "/c/40"                              || "https://www.skroutz.gr/c/40"
            "c/40"                               || "https://www.skroutz.gr/c/40"
    }

    @Unroll
    def "buildFullProductUrl returns complete URL as-is for '#url'"() {
        when: "building full URL from already complete URL"
            def result = urlBuilder.buildFullProductUrl(url)

        then: "URL is returned unchanged"
            result == url

        where:
            url << [
                    "http://www.skroutz.gr/products/123.html",
                    "https://www.skroutz.gr/products/123.html",
                    "http://other-domain.com/path",
                    "https://www.example.com/page"
            ]
    }

    @Unroll
    def "buildFullProductUrl returns null for '#scenario'"() {
        when: "building full URL from invalid input"
            def result = urlBuilder.buildFullProductUrl(input)

        then: "null is returned"
            result == null

        where:
            scenario       | input
            "null"         | null
            "empty string" | ""
            "blank string" | "   "
            "whitespace"   | "\t\n"
    }

    // buildReviewsApiUrl tests
    @Unroll
    def "buildReviewsApiUrl constructs correct URL for #scenario"() {
        when: "building reviews API URL"
            def result = urlBuilder.buildReviewsApiUrl(productUrl, offset)

        then: "correct API URL is returned"
            result == expected

        where:
            scenario                          | productUrl                                                    | offset || expected
            "simple .html URL"                | "https://www.skroutz.gr/s/123/product.html"                  | 0      || "https://www.skroutz.gr/s/123/product/reviews.json?offset=0&lang=en"
            "URL without extension"           | "https://www.skroutz.gr/s/123/product"                       | 0      || "https://www.skroutz.gr/s/123/product/reviews.json?offset=0&lang=en"
            "URL with query string"           | "https://www.skroutz.gr/s/123/product.html?ref=home"         | 0      || "https://www.skroutz.gr/s/123/product/reviews.json?offset=0&lang=en"
            "non-zero offset"                 | "https://www.skroutz.gr/s/123/product.html"                  | 10     || "https://www.skroutz.gr/s/123/product/reviews.json?offset=10&lang=en"
            "large offset"                    | "https://www.skroutz.gr/s/123/product.html"                  | 1000   || "https://www.skroutz.gr/s/123/product/reviews.json?offset=1000&lang=en"
            "complex path with .html"         | "https://www.skroutz.gr/s/123/product-name-here.html"        | 5      || "https://www.skroutz.gr/s/123/product-name-here/reviews.json?offset=5&lang=en"
            "URL with fragment"               | "https://www.skroutz.gr/s/123/product.html#section"          | 0      || "https://www.skroutz.gr/s/123/product/reviews.json?offset=0&lang=en"
    }

    @Unroll
    def "buildReviewsApiUrl throws exception for invalid input: #scenario"() {
        when: "building reviews API URL with invalid input"
            urlBuilder.buildReviewsApiUrl(productUrl, offset)

        then: "IllegalArgumentException is thrown"
            def exception = thrown(IllegalArgumentException)
            exception.message.contains(expectedMessage)

        where:
            scenario           | productUrl                          | offset || expectedMessage
            "null URL"         | null                                | 0      || "cannot be null or blank"
            "blank URL"        | "   "                               | 0      || "cannot be null or blank"
            "empty URL"        | ""                                  | 0      || "cannot be null or blank"
            "negative offset"  | "https://www.skroutz.gr/s/123.html" | -1     || "must be non-negative"
            "URL without path" | "https://www.skroutz.gr"            | 0      || "must have a path component"
            "invalid URL"      | "not a valid url"                   | 0      || "Invalid product URL"
    }

    // buildPriceGraphApiUrl tests
    @Unroll
    def "buildPriceGraphApiUrl constructs correct URL for #scenario"() {
        when: "building price graph API URL"
            def result = urlBuilder.buildPriceGraphApiUrl(productUrl)

        then: "correct API URL is returned"
            result == expected

        where:
            scenario                          | productUrl                                                    || expected
            "simple .html URL"                | "https://www.skroutz.gr/s/123/product.html"                  || "https://www.skroutz.gr/s/123/product/price_graph.json?shipping_country=GR&currency=EUR"
            "URL without extension"           | "https://www.skroutz.gr/s/123/product"                       || "https://www.skroutz.gr/s/123/product/price_graph.json?shipping_country=GR&currency=EUR"
            "URL with query string"           | "https://www.skroutz.gr/s/123/product.html?ref=home"         || "https://www.skroutz.gr/s/123/product/price_graph.json?shipping_country=GR&currency=EUR"
            "complex path with .html"         | "https://www.skroutz.gr/s/123/product-name-here.html"        || "https://www.skroutz.gr/s/123/product-name-here/price_graph.json?shipping_country=GR&currency=EUR"
            "URL with fragment"               | "https://www.skroutz.gr/s/123/product.html#section"          || "https://www.skroutz.gr/s/123/product/price_graph.json?shipping_country=GR&currency=EUR"
            "URL with multiple query params"  | "https://www.skroutz.gr/s/123/product.html?a=1&b=2"          || "https://www.skroutz.gr/s/123/product/price_graph.json?shipping_country=GR&currency=EUR"
    }

    @Unroll
    def "buildPriceGraphApiUrl throws exception for invalid input: #scenario"() {
        when: "building price graph API URL with invalid input"
            urlBuilder.buildPriceGraphApiUrl(productUrl)

        then: "IllegalArgumentException is thrown"
            def exception = thrown(IllegalArgumentException)
            exception.message.contains(expectedMessage)

        where:
            scenario           | productUrl                || expectedMessage
            "null URL"         | null                      || "cannot be null or blank"
            "blank URL"        | "   "                     || "cannot be null or blank"
            "empty URL"        | ""                        || "cannot be null or blank"
            "URL without path" | "https://www.skroutz.gr"  || "must have a path component"
            "invalid URL"      | "not a valid url"         || "Invalid product URL"
    }

    def "stripHtmlExtension only strips .html suffix"() {
        when: "building API URL with .html in the middle of path"
            def result = urlBuilder.buildReviewsApiUrl("https://www.skroutz.gr/s/123/product.html.backup", 0)

        then: "only trailing .html would be stripped, .backup remains"
            result.contains("https://www.skroutz.gr/s/123/product.html.backup/reviews.json")
    }
}
