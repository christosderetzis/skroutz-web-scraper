package org.skroutz.scraper.skroutzwebscraper.specs.scraping

import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product
import org.skroutz.scraper.skroutzwebscraper.review.domain.entity.Review
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.enums.ScrapeJobStatus
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec

import java.time.LocalDate


class ScrapeReviewsFunctionalSpec extends BaseFunctionalSpec {

    def "Scrape reviews with full data, happy path"() {
        given: "A product with a URL pointing to full reviews data"
            Product product = Product.builder()
                    .title("Gaming Laptop")
                    .price(329.99)
                    .imageUrl("http://example.com/image.jpg")
                    .url("http://localhost:8081/product-with-reviews.html")
                    .description("High performance gaming laptop")
                    .rating(4.5)
                    .reviewsParsed(false)
                    .priceHistoryParsed(false)
                    .build()
            productRepository.save(product)

        when: "we call the API for reviews"
            def response = webActor.scrapeReviews()
            webActor.waitForJobCompletion(response)

        then: "Product reviewsParsed is true"
            Product savedProduct = productRepository.findAll().getFirst()
            with(savedProduct) {
                reviewsParsed == true
            }

        and: "Exactly 2 review records are saved"
            List<Review> reviews = reviewRepository.findAll().sort { it.reviewDate }
            assert reviews.size() == 2

        and: "All records belong to the product"
            assert reviews.every { it.productId == product.id }

        and: "First record has the exact data"
            with(reviews[0]) {
                reviewerRating == 5
                reviewerName == "lazfotiadis"
                isVerifiedPurchase == false
                reviewDate == LocalDate.of(2025, 6, 10)
                helpfulVotes == 7
                totalVotes == 9
                reviewText == "The phone is simply wonderful! The photos are perfect."

                pros.sort() == [
                        "Call Quality",
                        "Photos",
                        "Video Recording",
                        "Music",
                        "Speed",
                        "Value for Money",
                        "Screen Resolution",
                        "Battery"
                ].sort()
                cons == null
                neutral == null
            }

        and: "Second record has the exact data"
            with(reviews[1]) {
                reviewerRating == 4
                reviewerName == "nikosz11"
                isVerifiedPurchase == true
                reviewDate == LocalDate.of(2025, 8, 1)
                helpfulVotes == 5
                totalVotes == 7
                reviewText == "Coming from 12. Changes exist, not crazy but they exist. Great cameras and screen."

                pros.sort() == [
                        "Call Quality",
                        "Photos",
                        "Video Recording",
                        "Music",
                        "Speed",
                        "Screen Resolution"
                ].sort()
                neutral == ["Value for Money"] as String[]
                cons == ["Battery"] as String[]
            }

        and: "The product is marked as parsed"
            def updatedProduct = productRepository.findById(product.getId()).get()
            assert updatedProduct.reviewsParsed == true

        and: "The job status is COMPLETED"
            def scrapeJob = scrapeJobRepository.findAll().first()
            assert scrapeJob.status == ScrapeJobStatus.COMPLETED
    }

    def "Scrape reviews with empty data, happy path"() {
        given: "A product with a URL pointing to empty reviews data"
            Product product = Product.builder()
                    .title("Gaming Laptop")
                    .price(329.99)
                    .imageUrl("http://example.com/image.jpg")
                    .url("http://localhost:8081/product-without-reviews.html")
                    .description("High performance gaming laptop")
                    .rating(4.5)
                    .reviewsParsed(false)
                    .priceHistoryParsed(false)
                    .build()
            productRepository.save(product)

        when: "we call the API for reviews"
            def response = webActor.scrapeReviews()
            webActor.waitForJobCompletion(response)

        then: "Product reviewsParsed is true"
            Product savedProduct = productRepository.findAll().getFirst()
            with(savedProduct) {
                reviewsParsed == true
            }

        and: "No review records are saved"
            List<Review> reviews = reviewRepository.findAll().sort { it.reviewDate }
            assert reviews.size() == 0

        and: "The status of the job is COMPLETED"
            def scrapeJob = scrapeJobRepository.findAll().first()
            assert scrapeJob.status == ScrapeJobStatus.COMPLETED
    }

    def "Scrape reviews handles API errors gracefully"() {
        given: "A product with a URL that returns an error"
            Product product = Product.builder()
                    .title("Error Product")
                    .price(99.99)
                    .imageUrl("http://example.com/image.jpg")
                    .url("http://localhost:8081/product-error.html")
                    .description("Product that will trigger an error")
                    .rating(3.0)
                    .reviewsParsed(false)
                    .priceHistoryParsed(false)
                    .build()
            productRepository.save(product)

        when: "The reviews API is called"
            def response = webActor.scrapeReviews()
            webActor.waitForJobCompletion(response)

        then: "No reviews data is saved for this product"
            def reviews = reviewRepository.findAll()
            assert reviews.size() == 0

        and: "The product is not marked as parsed due to the error"
            def updatedProduct = productRepository.findById(product.getId()).get()
            assert updatedProduct.reviewsParsed == false

        and: "The job status is COMPLETED"
            def scrapeJob = scrapeJobRepository.findAll().first()
            assert scrapeJob.status == ScrapeJobStatus.COMPLETED
    }
}
