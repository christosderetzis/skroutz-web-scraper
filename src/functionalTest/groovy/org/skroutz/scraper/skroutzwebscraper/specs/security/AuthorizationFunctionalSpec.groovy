package org.skroutz.scraper.skroutzwebscraper.specs.security

import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec

class AuthorizationFunctionalSpec extends BaseFunctionalSpec {

    def cleanup() {
        webActor.updateBearerToken(webActor.getAccessToken("admin", "admin"))
    }

    def "Scraping endpoint returns 401 when no bearer token is provided"() {
        given: "No Authorization header is set"
            webActor.updateBearerToken(null)

        when: "A scraping endpoint is called without an Authorization header"
            def response = webActor.scrapePriceHistory()

        then: "The API responds with 401 Unauthorized"
            response.expectStatus().isUnauthorized()
    }

    def "Scraping endpoint returns 403 when the user lacks the SUPER_ADMIN role"() {
        given: "A non-privileged user token is set"
            webActor.updateBearerToken(webActor.getAccessToken("user", "user"))

        when: "A scraping endpoint is called with a non-privileged user token"
            def response = webActor.scrapePriceHistory()

        then: "The API responds with 403 Forbidden"
            response.expectStatus().isForbidden()
    }
}
