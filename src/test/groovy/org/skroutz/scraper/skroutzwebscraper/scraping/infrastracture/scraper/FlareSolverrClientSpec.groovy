package org.skroutz.scraper.skroutzwebscraper.scraping.infrastracture.scraper

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.scraper.FlareSolverrClient
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.server.ResponseStatusException
import spock.lang.Specification
import spock.lang.Subject

class FlareSolverrClientSpec extends Specification {

    @Subject
    FlareSolverrClient client

    MockWebServer server

    def setup() {
        server = new MockWebServer()
        server.start()
        client = new FlareSolverrClient(
                server.url("/v1").toString(),
                5000,
                true
        )
    }

    def cleanup() {
        server.shutdown()
    }

    def "fetchJson unwraps Chromium's pre-wrapped JSON"() {
        given:
            String json = '{"skus":[],"page":{"total_pages":1}}'
            String wrapped = "<html><head></head><body><pre>${json}</pre></body></html>"
            server.enqueue(okResponse(wrapped))

        when:
            String result = client.fetchJson("https://www.skroutz.gr/c/40/test.json")

        then:
            result == json

        and:
            def request = server.takeRequest()
            request.path == "/v1"
            request.body.readUtf8().contains('"cmd":"request.get"')
    }

    def "fetchHtml creates a session and returns the raw HTML response"() {
        given:
            String html = "<html><body><div id=\"specs\">ok</div></body></html>"
            server.enqueue(sessionResponse("skroutz-html-test"))
            server.enqueue(okResponse(html))

        when:
            String result = client.fetchHtml("https://www.skroutz.gr/s/1/product.html")

        then:
            result == html

        and: "a session was created and then used for the request"
            server.takeRequest().body.readUtf8().contains('"cmd":"sessions.create"')
            server.takeRequest().body.readUtf8().contains('"session":"skroutz-html-test"')
    }

    def "fetchHtml reuses the same session across requests"() {
        given:
            server.enqueue(sessionResponse("skroutz-html-test"))
            server.enqueue(okResponse("<html><body>one</body></html>"))
            server.enqueue(okResponse("<html><body>two</body></html>"))

        when:
            String first = client.fetchHtml("https://www.skroutz.gr/s/1/product.html")
            String second = client.fetchHtml("https://www.skroutz.gr/s/2/product.html")

        then:
            first.contains("one")
            second.contains("two")

        and: "only one session was created"
            server.requestCount == 3
    }

    def "fetch throws BAD_GATEWAY when FlareSolverr reports an error"() {
        given:
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody('{"status":"error","message":"challenge failed"}'))

        when:
            client.fetchJson("https://www.skroutz.gr/c/40/test.json")

        then:
            def exception = thrown(ResponseStatusException)
            exception.statusCode == HttpStatus.BAD_GATEWAY
            exception.reason.contains("challenge failed")
    }

    def "isEnabled reflects the configured flag"() {
        expect:
            client.isEnabled()

        and:
            !new FlareSolverrClient(server.url("/v1").toString(), 1000, false).isEnabled()
    }

    private static MockResponse sessionResponse(String session) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"status\":\"ok\",\"session\":\"${session}\"}")
    }

    private static MockResponse okResponse(String body) {
        String escaped = new ObjectMapper().writeValueAsString(body)
        return new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"status\":\"ok\",\"solution\":{\"status\":200,\"response\":${escaped}}}")
    }
}
