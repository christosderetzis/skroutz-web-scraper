package org.skroutz.scraper.skroutzwebscraper

import org.springframework.modulith.core.ApplicationModules
import spock.lang.Specification

class ApplicationModulesSpec extends Specification {

    def "all application modules are discovered and verified"() {
        given:
        def modules = ApplicationModules.of(SkroutzWebScraperApplication.class).verify()

        expect:
        modules.collect { it.identifier.toString() }.containsAll(
                ["category", "common", "priceHistory", "product", "review", "scraping", "search"])
    }
}
