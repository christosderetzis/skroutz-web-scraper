package org.skroutz.scraper.skroutzwebscraper.scraping.infrastracture.scraper

import com.fasterxml.jackson.databind.JsonNode
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.scraper.SpecificationsScraper
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class SpecificationsScraperSpec extends Specification {

    @Subject
    SpecificationsScraper scraper = new SpecificationsScraper()

    MockWebServer server

    def setup() {
        server = new MockWebServer()
        server.start()
    }

    def cleanup() {
        server.shutdown()
    }

    def "scrapeSpecifications should successfully parse specifications"() {
        given: "an HTML page with specification groups"
            String html = """
                    <div id="specs">
                        <div class="specs-container content section">
                            <div class="spec-groups">
                                <div class="spec-details">
                                    <h3>General</h3>
                                    <dl><dt>Weight</dt><dd>2.5kg</dd></dl>
                                    <dl><dt>Dimensions</dt><dd>30x20x10cm</dd></dl>
                                </div>
                                <div class="spec-details">
                                    <h3>Technical</h3>
                                    <dl><dt>Power</dt><dd>100W</dd></dl>
                                </div>
                            </div>
                        </div>
                    </div>
                """
                Document doc = Jsoup.parse(html)

        when: "parsing specifications"
            JsonNode result = scraper.parseSpecifications(doc)

        then: "all categories and key-value pairs are parsed correctly"
            result.has("General")
            result.get("General").get("Weight").asText() == "2.5kg"
            result.get("General").get("Dimensions").asText() == "30x20x10cm"

            result.has("Technical")
            result.get("Technical").get("Power").asText() == "100W"
    }

    def "scrapeSpecifications should skip malformed dl elements gracefully"() {
        given: "an HTML page with a malformed dl"
            String html = """
                    <div id="specs">
                        <div class="specs-container content section">
                            <div class="spec-groups">
                                <div class="spec-details">
                                    <h3>General</h3>
                                    <dl><dt>Weight</dt><dd>2.5kg</dd></dl>
                                    <dl><dt></dt><dd></dd></dl> <!-- malformed -->
                                </div>
                            </div>
                        </div>
                    </div>
                """
            Document doc = Jsoup.parse(html)

        when:
            JsonNode result = scraper.parseSpecifications(doc)

        then: "only valid dl is parsed"
            result.has("General")
            result.get("General").get("Weight").asText() == "2.5kg"
            result.get("General").size() == 1
    }

    def "scrapeSpecifications should return empty object when no specification groups found"() {
        given: "an empty HTML page"
            String html = "<html><body></body></html>"
            Document doc = Jsoup.parse(html)

        when:
            JsonNode result = scraper.parseSpecifications(doc)

        then:
            result != null
            result.size() == 0
    }

    @Unroll
    def "scrapeSpecifications should handle multiple categories correctly"() {
        given:
        String html = """
                <div id="specs">
                    <div class="specs-container content section">
                        <div class="spec-groups">
                            <div class="spec-details"><h3>${category}</h3>
                                <dl><dt>${key}</dt><dd>${value}</dd></dl>
                            </div>
                        </div>
                    </div>
                </div>
            """
        Document doc = Jsoup.parse(html)

        when:
        JsonNode result = scraper.parseSpecifications(doc)

        then:
        result.has(category)
        result.get(category).get(key).asText() == value

        where:
        category      | key      | value
        "General"     | "Weight" | "2.5kg"
        "Technical"   | "Power"  | "100W"
        "Dimensions"  | "Size"   | "30x20x10cm"
    }

    def "scrapeSpecifications handles HttpStatusException gracefully and returns empty Optional"() {
        given: "a mock server configured to return an HTTP error status code"
            // Jsoup throws HttpStatusException for 4xx/5xx responses
            server.enqueue(new MockResponse().setResponseCode(404).setBody("Not Found"))
            String baseUrl = server.url("/test-product").toString()

        when: "scraping a URL that returns an HTTP error code"
            Optional<JsonNode> result = scraper.scrapeSpecifications(baseUrl)

        then: "the exception is caught, logged as a warning, and an empty Optional is returned"
            noExceptionThrown()
            result.isEmpty()
    }

    def "scrapeSpecifications handles generic Exception gracefully and returns empty Optional"() {
        given: "a malformed or unreachable URL that causes Jsoup to throw a generic Exception"
            // For example, an invalid protocol or an unresolvable host
            // will throw a java.net.MalformedURLException or java.net.UnknownHostException
            String invalidUrl = "invalid-protocol://this.is.not.a.valid.url"

        when: "scraping the bad URL"
            Optional<JsonNode> result = scraper.scrapeSpecifications(invalidUrl)

        then: "the generic exception is caught, logged as an error, and an empty Optional is returned"
            noExceptionThrown()
            result.isEmpty()
    }
}
