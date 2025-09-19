package org.skroutz.scraper.skroutzwebscraper.scheduled

import org.skroutz.scraper.skroutzwebscraper.service.SpecificationsService
import spock.lang.Specification

class SpecificationsSchedulerSpec extends Specification {

    SpecificationsService specificationsService
    SpecificationsScheduler specificationsScheduler

    def setup() {
        specificationsService = Mock(SpecificationsService)
        specificationsScheduler = new SpecificationsScheduler(specificationsService)
    }

    def "should call specificationsService.scrapeSpecifications()"() {
        when:
            specificationsScheduler.parseSpecifications()

        then:
            1 * specificationsService.parseSpecifications()
            0 * _
    }
}
