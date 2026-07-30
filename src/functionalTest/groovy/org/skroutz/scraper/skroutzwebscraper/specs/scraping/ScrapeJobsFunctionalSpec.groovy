package org.skroutz.scraper.skroutzwebscraper.specs.scraping

import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.entity.ScrapeJob
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.enums.ScrapeJobStatus
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.enums.ScrapeJobType
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.springframework.test.web.reactive.server.WebTestClient

import java.time.LocalDateTime

class ScrapeJobsFunctionalSpec extends BaseFunctionalSpec {

    def "Scrape price history with full data where there is a stale job db record, happy path"() {
        given: "A product"
            Product product = Product.builder()
                    .title("Gaming Laptop")
                    .price(329.99)
                    .imageUrl("http://example.com/image.jpg")
                    .url("http://localhost:8081/product-full.html")
                    .description("High performance gaming laptop")
                    .rating(4.5)
                    .reviewsParsed(false)
                    .priceHistoryParsed(false)
                    .build()
            productRepository.save(product)

        and: "A stale scrape job record in the database"
            LocalDateTime threeHoursAgo = LocalDateTime.now().minusHours(3)
            ScrapeJob existingJob = scrapeJobRepository.saveAndFlush(
                    ScrapeJob.builder()
                            .status(ScrapeJobStatus.RUNNING)
                            .jobType(ScrapeJobType.SCRAPE_PRICE_HISTORY)
                            .startedAt(threeHoursAgo)
                            .build()
            )

        when: "The price history scraping endpoint is called"
            WebTestClient.ResponseSpec response = webActor.scrapePriceHistory()

        then: "The API response should immediately indicate that the existing job is running"
            String responseBody = response.expectBody(String).returnResult().getResponseBody()
            String expectedResponseBody = """
                            {
                                "id": ${existingJob.id + 1},
                                "jobType": "SCRAPE_PRICE_HISTORY",
                                "status": "RUNNING"
                            }
                            """
            JSONAssert.assertEquals(expectedResponseBody, responseBody, JSONCompareMode.LENIENT)

        when: "Wait for the job to complete"
            webActor.waitForJobCompletion(response, existingJob.id + 1)

        then: "Exactly 5 price history records are saved"
            def priceHistories = priceHistoryRepository.findAll().sort { it.priceDate }
            assert priceHistories.size() == 5

        and: "All records belong to the product"
            assert priceHistories.every { it.productId == product.getId() }

        and: "The product is marked as parsed"
            def updatedProduct = productRepository.findById(product.getId()).get()
            assert updatedProduct.priceHistoryParsed == true

        and: "The job existing status is FAILED"
            def oldJob = scrapeJobRepository.findAll().stream().filter { it.id == existingJob.id }.findFirst().get()
            assert oldJob.status == ScrapeJobStatus.FAILED
            assert oldJob.error == "Expired: job exceeded maximum runtime of 2h"

        and: "The new job status is COMPLETED"
            def newJob = scrapeJobRepository.findAll().stream().filter { it.id != existingJob.id }.findFirst().get()
            assert newJob.status == ScrapeJobStatus.COMPLETED
    }

    def "Scrape price history if there is another process already running"() {
        given: "A scrape job is already running"
            def existingJob = scrapeJobRepository.saveAndFlush(
                    ScrapeJob.builder()
                            .status(ScrapeJobStatus.RUNNING)
                            .jobType(ScrapeJobType.SCRAPE_PRICE_HISTORY)
                            .startedAt(LocalDateTime.now())
                            .build()
            )

        when: "The price history scraping endpoint is called"
            def response = webActor.scrapePriceHistory()

        then: "The API should not process the request and return a message indicating that another job is running"
            String responseBody = response.expectBody(String).returnResult().getResponseBody()
            String expectedResponseBody = """
                        {
                            "status": 409,
                            "method": "POST",
                            "errors": ["A ${existingJob.jobType.name()} job is already running (id: ${existingJob.id})"],
                            "path": "/scraper/price-history"
                        }
                        """
            JSONAssert.assertEquals(expectedResponseBody, responseBody, JSONCompareMode.LENIENT)

        then: "No price history data is saved for this product"
            def priceHistories = priceHistoryRepository.findAll()
            assert priceHistories.size() == 0

        and: "The job status of the existing job remains RUNNING"
            def existingJobAfter = scrapeJobRepository.findById(existingJob.id).get()
            assert existingJobAfter.status == ScrapeJobStatus.RUNNING
    }
}
