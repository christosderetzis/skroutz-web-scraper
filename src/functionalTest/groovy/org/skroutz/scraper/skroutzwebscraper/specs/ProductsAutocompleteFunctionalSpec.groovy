package org.skroutz.scraper.skroutzwebscraper.specs

import org.skroutz.scraper.skroutzwebscraper.dto.ProductSuggestionDto
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec

class ProductsAutocompleteFunctionalSpec extends BaseFunctionalSpec {

    def "Happy path - Autocomplete returns matching products"() {
        given: "products indexed in Elasticsearch"
            def macbookPro = createAndIndexProduct("MacBook Pro 16-inch")
            def macbookAir = createAndIndexProduct("MacBook Air 13-inch")
            def iPadPro = createAndIndexProduct("iPad Pro 12.9-inch")

        when: "searching for 'macbook'"
            def response = webActor.autocomplete("macbook")

        then: "response should be 200 OK"
            response.expectStatus().isOk()

        and: "should return only MacBook products"
            List<ProductSuggestionDto> suggestions = extractResponseList(response, ProductSuggestionDto)
            suggestions.size() == 2
            suggestions.any { it.id == macbookPro.id && it.title == macbookPro.title }
            suggestions.any { it.id == macbookAir.id && it.title == macbookAir.title }
            !suggestions.any { it.id == iPadPro.id }
    }

    def "Happy path - Autocomplete respects limit parameter"() {
        given: "multiple matching products indexed in Elasticsearch"
            createAndIndexProduct("MacBook Pro 16-inch")
            createAndIndexProduct("MacBook Air 13-inch")
            createAndIndexProduct("MacBook Pro 14-inch")

        when: "searching with limit of 2"
            def response = webActor.autocomplete("macbook", 2)

        then: "response should be 200 OK"
            response.expectStatus().isOk()

        and: "should return at most 2 results"
            List<ProductSuggestionDto> suggestions = extractResponseList(response, ProductSuggestionDto)
            suggestions.size() <= 2
    }

    def "Happy path - Autocomplete uses default limit of 5"() {
        given: "10 matching products indexed in Elasticsearch"
            (1..10).each { createAndIndexProduct("MacBook Pro ${it}-inch") }

        when: "searching without specifying limit"
            def response = webActor.autocomplete("macbook")

        then: "response should be 200 OK"
            response.expectStatus().isOk()

        and: "should return at most 5 results (default)"
            List<ProductSuggestionDto> suggestions = extractResponseList(response, ProductSuggestionDto)
            suggestions.size() <= 5
    }

    def "Happy path - Autocomplete is case insensitive"() {
        given: "a product indexed in Elasticsearch"
            def product = createAndIndexProduct("MacBook Pro 16-inch")

        when: "searching with different case variations"
            def responseLowercase = webActor.autocomplete("macbook")
            def responseUppercase = webActor.autocomplete("MACBOOK")
            def responseMixedCase = webActor.autocomplete("MaCbOoK")

        then: "all responses should return the same product"
            def suggestionsLowercase = extractResponseList(responseLowercase, ProductSuggestionDto)
            def suggestionsUppercase = extractResponseList(responseUppercase, ProductSuggestionDto)
            def suggestionsMixedCase = extractResponseList(responseMixedCase, ProductSuggestionDto)

            suggestionsLowercase.size() == 1
            suggestionsUppercase.size() == 1
            suggestionsMixedCase.size() == 1
            suggestionsLowercase[0].id == product.id
            suggestionsUppercase[0].id == product.id
            suggestionsMixedCase[0].id == product.id
    }

    def "Happy path - Autocomplete matches phrase prefix (all terms must be present in order)"() {
        given: "products with different combinations of words"
            def macbookNeo = createAndIndexProduct("MacBook Neo 15-inch")
            def macbookPro = createAndIndexProduct("MacBook Pro 16-inch")
            def neoMacbook = createAndIndexProduct("Neo MacBook Special Edition")

        when: "searching for 'macbook neo'"
            def response = webActor.autocomplete("macbook neo")

        then: "response should be 200 OK"
            response.expectStatus().isOk()

        and: "should only return products with 'macbook' followed by a word starting with 'neo'"
            List<ProductSuggestionDto> suggestions = extractResponseList(response, ProductSuggestionDto)
            suggestions.size() == 1
            suggestions[0].id == macbookNeo.id
            suggestions[0].title == macbookNeo.title
            !suggestions.any { it.id == macbookPro.id }
            !suggestions.any { it.id == neoMacbook.id }
    }

    def "Happy path - Autocomplete works with partial last word"() {
        given: "products indexed in Elasticsearch"
            def product = createAndIndexProduct("iPhone 15 Pro Max")

        when: "searching with partial last word"
            def response = webActor.autocomplete("iphone 15 pr")

        then: "response should be 200 OK"
            response.expectStatus().isOk()

        and: "should match the product"
            List<ProductSuggestionDto> suggestions = extractResponseList(response, ProductSuggestionDto)
            suggestions.size() == 1
            suggestions[0].id == product.id
    }

    def "Unhappy path - Autocomplete returns empty list when no matches"() {
        given: "products indexed in Elasticsearch"
            createAndIndexProduct("MacBook Pro 16-inch")
            createAndIndexProduct("iPad Pro 12.9-inch")

        when: "searching for non-existent product"
            def response = webActor.autocomplete("samsung galaxy")

        then: "response should be 200 OK"
            response.expectStatus().isOk()

        and: "should return empty list"
            List<ProductSuggestionDto> suggestions = extractResponseList(response, ProductSuggestionDto)
            suggestions.isEmpty()
    }

    def "Unhappy path - Autocomplete returns empty list when query doesn't match phrase order"() {
        given: "a product indexed in Elasticsearch"
            createAndIndexProduct("MacBook Pro 16-inch")

        when: "searching with words in wrong order"
            def response = webActor.autocomplete("pro macbook")

        then: "response should be 200 OK"
            response.expectStatus().isOk()

        and: "should return empty list (phrase must match in order)"
            List<ProductSuggestionDto> suggestions = extractResponseList(response, ProductSuggestionDto)
            suggestions.isEmpty()
    }
}
