package org.skroutz.scraper.skroutzwebscraper.specs.security

import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec

class AuthorizationFunctionalSpec extends BaseFunctionalSpec {

    def "Scraping endpoint returns 401 when no bearer token is provided"() {
        when: "A scraping endpoint is called without an Authorization header"
            def response = webActor.scrapePriceHistory(null)

        then: "The API responds with 401 Unauthorized"
            response.expectStatus().isUnauthorized()
    }

    def "Scraping endpoint returns 403 when the user lacks the SUPER_ADMIN role"() {
        when: "A scraping endpoint is called with a non-privileged user token"
            def response = webActor.scrapePriceHistory(webActor.getAccessToken("user", "user"))

        then: "The API responds with 403 Forbidden"
            response.expectStatus().isForbidden()
    }
}
