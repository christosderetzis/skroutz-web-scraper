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
            server.enqueue(solutionResponse(200, '{"status":"ok","session":"skroutz-html-test"}'))
            server.enqueue(solutionResponse(200, wrapped))

        when:
            String result = client.fetchJson("https://www.skroutz.gr/c/40/test.json")

        then:
            result == json

        and: "session is created first"
            def sessionReq = server.takeRequest()
            sessionReq.path == "/v1"
            sessionReq.body.readUtf8().contains('"cmd":"sessions.create"')

        and: "then the fetch is performed"
            def fetchReq = server.takeRequest()
            fetchReq.path == "/v1"
            fetchReq.body.readUtf8().contains('"cmd":"request.get"')
    }

    def "fetchHtml creates a session and returns the raw HTML response"() {
        given:
            String html = "<html><body><div id=\"specs\">ok</div></body></html>"
            server.enqueue(sessionResponse("skroutz-html-test"))
            server.enqueue(solutionResponse(200, html))

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
            server.enqueue(solutionResponse(200, "<html><body>one</body></html>"))
            server.enqueue(solutionResponse(200, "<html><body>two</body></html>"))

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

    def "fetch recreates the session when FlareSolverr reports it is missing"() {
        given:
            server.enqueue(sessionResponse("skroutz-html-first"))
            server.enqueue(errorResponse("session not found"))
            server.enqueue(sessionResponse("skroutz-html-second"))
            server.enqueue(solutionResponse(200, "<html><body>retried</body></html>"))

        when:
            String result = client.fetchHtml("https://www.skroutz.gr/s/1/product.html")

        then:
            result.contains("retried")

        and: "two sessions were created"
            server.requestCount == 4
            server.takeRequest().body.readUtf8().contains('"sessions.create"')
            server.takeRequest().body.readUtf8().contains('"skroutz-html-first"')
            server.takeRequest().body.readUtf8().contains('"sessions.create"')
            server.takeRequest().body.readUtf8().contains('"skroutz-html-second"')
    }

    def "fetch does not retry when the error is unrelated to the session"() {
        given:
            server.enqueue(sessionResponse("skroutz-html-test"))
            server.enqueue(errorResponse("challenge failed"))

        when:
            client.fetchHtml("https://www.skroutz.gr/s/1/product.html")

        then:
            def exception = thrown(ResponseStatusException)
            exception.statusCode == HttpStatus.BAD_GATEWAY
            server.requestCount == 2
    }

    def "fetch throws when FlareSolverr returns an HTTP error status"() {
        given:
            server.enqueue(sessionResponse("skroutz-html-test"))
            server.enqueue(solutionResponse(404, "not here"))

        when:
            client.fetchHtml("https://www.skroutz.gr/s/1/product.html")

        then:
            def exception = thrown(ResponseStatusException)
            exception.statusCode == HttpStatus.NOT_FOUND
    }

    def "fetch throws BAD_GATEWAY when the response body is empty"() {
        given:
            server.enqueue(sessionResponse("skroutz-html-test"))
            server.enqueue(solutionResponse(200, ""))

        when:
            client.fetchHtml("https://www.skroutz.gr/s/1/product.html")

        then:
            def exception = thrown(ResponseStatusException)
            exception.statusCode == HttpStatus.BAD_GATEWAY
            exception.reason.contains("empty body")
    }

    def "fetch wraps transport failures in BAD_GATEWAY"() {
        given:
            server.enqueue(sessionResponse("skroutz-html-test"))
            server.enqueue(new MockResponse().setResponseCode(500))

        when:
            client.fetchHtml("https://www.skroutz.gr/s/1/product.html")

        then:
            def exception = thrown(ResponseStatusException)
            exception.statusCode == HttpStatus.BAD_GATEWAY
    }

    def "fetchJson returns the body unchanged when it is not pre-wrapped"() {
        given:
            String json = '{"plain":true}'
            server.enqueue(sessionResponse("skroutz-html-test"))
            server.enqueue(solutionResponse(200, json))

        expect:
            client.fetchJson("https://www.skroutz.gr/c/40/test.json") == json
    }

    def "fetchJson returns the original body when the pre content is not valid JSON"() {
        given:
            String body = "<html><body><pre>not json</pre></body></html>"
            server.enqueue(sessionResponse("skroutz-html-test"))
            server.enqueue(solutionResponse(200, body))

        when:
            def response = client.fetchJson("https://www.skroutz.gr/c/40/test.json")

        then:
            response == body
    }

    def "destroySession destroys the active session"() {
        given:
            server.enqueue(sessionResponse("skroutz-html-test"))
            server.enqueue(solutionResponse(200, "<html><body>ok</body></html>"))
            server.enqueue(sessionResponse("skroutz-html-test"))
            client.fetchHtml("https://www.skroutz.gr/s/1/product.html")
            server.takeRequest()
            server.takeRequest()

        when:
            client.destroySession()

        then: "a sessions.destroy command was sent"
            server.takeRequest().body.readUtf8().contains('"sessions.destroy"')
    }

    def "destroySession is a no-op when no session was created"() {
        when:
            client.destroySession()

        then:
            server.requestCount == 0
    }

    def "destroySession swallows failures"() {
        given:
            server.enqueue(sessionResponse("skroutz-html-test"))
            server.enqueue(solutionResponse(200, "<html><body>ok</body></html>"))
            server.enqueue(new MockResponse().setResponseCode(500))
            client.fetchHtml("https://www.skroutz.gr/s/1/product.html")
            server.takeRequest()
            server.takeRequest()

        when:
            client.destroySession()

        then:
            noExceptionThrown()
    }

    private static MockResponse errorResponse(String message) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"status\":\"error\",\"message\":\"${message}\"}")
    }

    private static MockResponse solutionResponse(int status, String body) {
        String escaped = new ObjectMapper().writeValueAsString(body)
        return new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"status\":\"ok\",\"solution\":{\"status\":${status},\"response\":${escaped}}}")
    }

    private static MockResponse sessionResponse(String session) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"status\":\"ok\",\"session\":\"${session}\"}")
    }
}
