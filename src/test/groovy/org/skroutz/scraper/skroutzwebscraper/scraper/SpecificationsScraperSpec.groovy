package org.skroutz.scraper.skroutzwebscraper.scraper

import com.fasterxml.jackson.databind.JsonNode
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class SpecificationsScraperSpec extends Specification {

    @Subject
    SpecificationsScraper scraper = new SpecificationsScraper()

    def "parseSpecifications skips entries with empty key or value"() {
        given:
            String html = """
                <div id="specs">
                    <div class="specs-container content section">
                        <div class="spec-groups">
                            <div class="spec-details">
                                <h3>General</h3>
                                <dl><dt>Weight</dt><dd>100W</dd></dl>
                                <dl><dt></dt><dd></dd></dl>
                                <dl><dt>Empty</dt><dd></dd></dl>
                                <dl><dt></dt><dd>100</dd></dl>
                            </div>
                        </div>
                    </div>
                </div>
            """
            Document doc = Jsoup.parse(html)

        when:
            JsonNode result = scraper.parseSpecifications(doc)
            JsonNode array = result.get("General")

        then:
            array.size() == 1
            findByKey(array, "Weight") != null
    }

    def "parseSpecifications returns empty object for empty HTML"() {
        when:
            JsonNode result = scraper.parseSpecifications(Jsoup.parse("<html><body></body></html>"))

        then:
            result != null
            result.size() == 0
    }

    def "parseSpecifications handles multiple categories"() {
        given:
            String html = """
                <div id="specs">
                    <div class="specs-container content section">
                        <div class="spec-groups">
                            <div class="spec-details">
                                <h3>Βασικά Χαρακτηριστικά</h3>
                                <dl><dt>Διαγώνιος</dt><dd>24 mm</dd></dl>
                                <dl><dt>Ανάλυση</dt><dd>1920x1080</dd></dl>
                            </div>
                            <div class="spec-details">
                                <h3>Τεχνικά Χαρακτηριστικά</h3>
                                <dl><dt>Ρυθμός Ανανέωσης</dt><dd>180 Hz</dd></dl>
                                <dl><dt>Χρόνος Απόκρισης</dt><dd>1 ms</dd></dl>
                            </div>
                        </div>
                    </div>
                </div>
            """
            Document doc = Jsoup.parse(html)

        when:
            JsonNode result = scraper.parseSpecifications(doc)

        then:
            result.has("Βασικά Χαρακτηριστικά")
            result.has("Τεχνικά Χαρακτηριστικά")

            def basics = result.get("Βασικά Χαρακτηριστικά")
            basics.isArray() && basics.size() == 2
            findByKey(basics, "Διαγώνιος").get("value").longValue() == 24
            findByKey(basics, "Διαγώνιος").get("unit").asText() == "mm"
            findByKey(basics, "Ανάλυση").get("value").asText() == "1920x1080"
            !findByKey(basics, "Ανάλυση").has("unit")

            def technical = result.get("Τεχνικά Χαρακτηριστικά")
            findByKey(technical, "Ρυθμός Ανανέωσης").get("value").longValue() == 180
            findByKey(technical, "Ρυθμός Ανανέωσης").get("unit").asText() == "Hz"
            findByKey(technical, "Χρόνος Απόκρισης").get("value").longValue() == 1
            findByKey(technical, "Χρόνος Απόκρισης").get("unit").asText() == "ms"
    }

    @Unroll
    def "parseSpecifications parses '#rawValue' → value=#expectedValue unit=#expectedUnit"() {
        given:
            String html = buildHtml("Cat", ["Key": rawValue])
            Document doc = Jsoup.parse(html)

        when:
            JsonNode result = scraper.parseSpecifications(doc)
            def entry = findByKey(result.get("Cat"), "Key")

        then:
            entry.get("value").asText() == expectedValue.toString()
            expectedUnit == null ? !entry.has("unit") : entry.get("unit").asText() == expectedUnit

        where:
            rawValue           | expectedValue    | expectedUnit
            "100W"             | 100              | "W"
            "180 Hz"           | 180              | "Hz"
            "2.5 kg"           | 2.5              | "kg"
            "72,8 cfm"         | 72.8             | "cfm"
            "36 dB"            | 36               | "dB"
            "3 τμχ"            | 3                | "τμχ"
            "4"                | 4                | null
            "1920x1080"        | "1920x1080"      | null
            "16:9"             | "16:9"           | null
            "Μαύρο"            | "Μαύρο"          | null
            "Gaming Monitor"   | "Gaming Monitor" | null
            "4-Pin PWM"        | "4-Pin PWM"      | null
            "ARGB"             | "ARGB"           | null
    }

    private static JsonNode findByKey(JsonNode array, String key) {
        for (int i = 0; i < array.size(); i++) {
            if (array.get(i).get("key").asText() == key) return array.get(i)
        }
        return null
    }

    private static String buildHtml(String category, Map<String, String> specs) {
        def dls = specs.collect { k, v -> "<dl><dt>${k}</dt><dd>${v}</dd></dl>" }.join("\n")
        """
            <div id="specs">
                <div class="specs-container content section">
                    <div class="spec-groups">
                        <div class="spec-details">
                            <h3>${category}</h3>
                            ${dls}
                        </div>
                    </div>
                </div>
            </div>
        """
    }
}
