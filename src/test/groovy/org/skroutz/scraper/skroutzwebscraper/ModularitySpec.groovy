package org.skroutz.scraper.skroutzwebscraper

import org.springframework.modulith.core.ApplicationModules
import spock.lang.Specification

class ModularitySpec extends Specification {

    def "application modules should be valid"() {
        given:
            ApplicationModules modules = ApplicationModules.of(SkroutzWebScraperApplication)

        expect:
            modules.verify()
    }
}
