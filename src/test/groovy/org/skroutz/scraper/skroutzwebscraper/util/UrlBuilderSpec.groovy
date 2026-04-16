package org.skroutz.scraper.skroutzwebscraper.util

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
}
