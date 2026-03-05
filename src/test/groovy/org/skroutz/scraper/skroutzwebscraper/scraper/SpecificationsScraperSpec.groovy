package org.skroutz.scraper.skroutzwebscraper.scraper

import com.fasterxml.jackson.databind.JsonNode

import org.springframework.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class SpecificationsScraperSpec extends Specification {

    ApplicationContext applicationContext = Mock(ApplicationContext)

    @Subject
    SpecificationsScraper scraper = new SpecificationsScraper(applicationContext, "https://www.skroutz.gr")

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

        when: "parsing specifications"
            JsonNode result = scraper.parseSpecifications(html)

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

        when:
            JsonNode result = scraper.parseSpecifications(html)

        then: "only valid dl is parsed"
            result.has("General")
            result.get("General").get("Weight").asText() == "2.5kg"
            result.get("General").size() == 1
    }

    def "scrapeSpecifications should return empty object when no specification groups found"() {
        given: "an empty HTML page"
            String html = "<html><body></body></html>"

        when:
            JsonNode result = scraper.parseSpecifications(html)

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

        when:
            JsonNode result = scraper.parseSpecifications(html)

        then:
            result.has(category)
            result.get(category).get(key).asText() == value

        where:
            category      | key      | value
            "General"     | "Weight" | "2.5kg"
            "Technical"   | "Power"  | "100W"
            "Dimensions"  | "Size"   | "30x20x10cm"
    }
}
