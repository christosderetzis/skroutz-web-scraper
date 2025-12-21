package org.skroutz.scraper.skroutzwebscraper.scraper

import com.fasterxml.jackson.databind.JsonNode
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.springframework.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Subject

class SpecificationsScraperSpec extends Specification {

    ApplicationContext applicationContext = Mock()
    WebDriver webDriver = Mock()

    @Subject
    SpecificationsScraper scraper = new SpecificationsScraper(applicationContext)

    def "scrapeSpecifications should successfully parse specifications"() {
        given: "a URL to scrape"
            String url = "http://example.com/product"

        and: "application context returns webdriver"
            applicationContext.getBean(WebDriver.class) >> webDriver

        and: "mock specification groups with categories and key-value pairs"
            def specs = [
                    "General"  : ["Weight": "2.5kg", "Dimensions": "30x20x10cm"],
                    "Technical": ["Power" : "100W"]
            ]

            def specGroups = specs.collect { category, kvs ->
                def group = Mock(WebElement)
                def h3 = Mock(WebElement) { getText() >> category }
                def dls = kvs.collect { key, value ->
                    def dl = Mock(WebElement)
                    def dt = Mock(WebElement) { getText() >> key }
                    def dd = Mock(WebElement) { getText() >> value }
                    dl.findElement(By.tagName("dt")) >> dt
                    dl.findElement(By.tagName("dd")) >> dd
                    dl
                }
                group.findElement(By.tagName("h3")) >> h3
                group.findElements(By.tagName("dl")) >> dls
                group
            }

        and: "webdriver finds specification groups"
            webDriver.findElements(By.cssSelector(HtmlFields.SPECIFICATIONS)) >> specGroups

        when: "scraping specifications"
            JsonNode result = scraper.screapeSpecifications(url)

        then: "webdriver should navigate to URL"
            1 * webDriver.get(url)

        and: "result JSON should contain all categories"
            specs.each { category, kvs ->
                assert result.has(category)
                kvs.each { key, value ->
                    assert result.get(category).get(key).asText() == value
                }
            }

        and: "webdriver should be closed"
            1 * webDriver.quit()
    }

    def "scrapeSpecifications should handle malformed dl elements gracefully new"() {
        given: "a URL to scrape"
            String url = "http://example.com/product"
            applicationContext.getBean(WebDriver.class) >> webDriver

            def specs = ["General": ["Weight": "2.5kg"]]
            def specGroups = specs.collect { category, kvs ->
                def group = Mock(WebElement)
                def h3 = Mock(WebElement) { getText() >> category }

                def dls = kvs.collect { key, value ->
                    def dl = Mock(WebElement)
                    dl.findElement(By.tagName("dt")) >> Mock(WebElement) { getText() >> key }
                    dl.findElement(By.tagName("dd")) >> Mock(WebElement) { getText() >> value }
                    dl
                }

                // Malformed dl
                def malformedDl = Mock(WebElement)
                malformedDl.findElement(By.tagName("dt")) >> { throw new NoSuchElementException("Element not found") }
                malformedDl.findElement(By.tagName("dd")) >> { throw new NoSuchElementException("Element not found") }
                dls.add(malformedDl)

                group.findElement(By.tagName("h3")) >> h3
                group.findElements(By.tagName("dl")) >> dls
                group
            }

            webDriver.findElements(By.cssSelector(HtmlFields.SPECIFICATIONS)) >> specGroups

        when: "scraping specifications"
            JsonNode result = scraper.screapeSpecifications(url)

        then: "webdriver should navigate to URL"
            1 * webDriver.get(url)
            1 * webDriver.quit()

        and: "should return JSON with only valid specifications"
            specs.each { category, kvs ->
                assert result.has(category)
                kvs.each { key, value ->
                    assert result.get(category).get(key).asText() == value
                }
            }
    }

    def "scrapeSpecifications should return null when webdriver operation fails"() {
        given: "a URL to scrape"
            String url = "http://example.com/product"

        and: "application context returns webdriver"
            applicationContext.getBean(WebDriver.class) >> webDriver

        and: "webdriver.get throws exception"
            webDriver.get(url) >> { throw new RuntimeException("Navigation failed") }

        when: "scraping specifications"
            JsonNode result = scraper.screapeSpecifications(url)

        then: "should return null"
            result == null

        and: "webdriver should be closed"
            1 * webDriver.quit()
    }

    def "scrapeSpecifications should return empty object when no specification groups found"() {
        given: "a URL to scrape"
            String url = "http://example.com/product"

        and: "application context returns webdriver"
            applicationContext.getBean(WebDriver.class) >> webDriver

        and: "webdriver finds no specification groups"
            webDriver.findElements(By.cssSelector(HtmlFields.SPECIFICATIONS)) >> []

        when: "scraping specifications"
            JsonNode result = scraper.screapeSpecifications(url)

        then: "webdriver should navigate to URL"
            1 * webDriver.get(url)

        and: "should return empty JSON object"
            result != null
            result.size() == 0

        and: "webdriver should be closed"
            1 * webDriver.quit()
    }
}
