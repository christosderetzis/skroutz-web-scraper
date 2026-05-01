package org.skroutz.scraper.skroutzwebscraper.specs

import org.skroutz.scraper.skroutzwebscraper.agent.ReviewSummarizer
import org.skroutz.scraper.skroutzwebscraper.dto.ReviewSummary
import org.skroutz.scraper.skroutzwebscraper.entity.Product
import org.skroutz.scraper.skroutzwebscraper.entity.Review
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec
import org.spockframework.spring.SpringBean
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode

class SummarizeReviewsFunctionalSpec extends BaseFunctionalSpec {

    // Mock the ReviewSummarizer to return fixed summaries for testing, since LLMs are non-deterministic and we want consistent test results.
    // The summarization logic is tested separately in unit tests for the ReviewSummarizer.
    @SpringBean
    ReviewSummarizer reviewSummarizer = Stub()

    def "Happy path - single chunk summarization"() {
        given: "A product with parsed reviews"
            Product product = productRepository.saveAndFlush(Product.builder()
                    .title("Samsung Galaxy A56")
                    .url("http://example.com/samsung-galaxy-a56")
                    .description("Mid-range smartphone")
                    .price(329.99)
                    .rating(4.5)
                    .reviewsParsed(true)
                    .build())

            reviewRepository.saveAllAndFlush([
                    Review.builder()
                            .productId(product.id)
                            .reviewerName("user1")
                            .reviewerRating(5)
                            .reviewText("Great phone, excellent battery life")
                            .isVerifiedPurchase(true)
                            .build(),
                    Review.builder()
                            .productId(product.id)
                            .reviewerName("user2")
                            .reviewerRating(4)
                            .reviewText("Good value for the price")
                            .isVerifiedPurchase(false)
                            .build()
            ])

        and: "The summarizer returns a fixed response"
            reviewSummarizer.summarizeChunk(_, _, _) >> new ReviewSummary(
                    "Excellent mid-range phone with great battery.",
                    ["Battery life", "Value for money"],
                    ["Camera in low light"],
                    "Positive"
            )

        when: "We call the summarize endpoint"
            def response = webActor.summarizeReviews(product.id)

        then: "The response is 200 with the summary"
            response.expectStatus().isOk()

            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "summary": "Excellent mid-range phone with great battery.",
                    "pros": ["Battery life", "Value for money"],
                    "cons": ["Camera in low light"],
                    "sentiment": "Positive"
                }
            """, body, JSONCompareMode.STRICT)
    }

    def "Happy path - multi chunk summarization"() {
        given: "A product with many large reviews that exceed chunk size"
            Product product = productRepository.saveAndFlush(Product.builder()
                    .title("Samsung Galaxy A56")
                    .url("http://example.com/samsung-galaxy-a56")
                    .description("Mid-range smartphone")
                    .price(329.99)
                    .rating(4.5)
                    .reviewsParsed(true)
                    .build())

            String largeReviewText = "A" * 7000
            reviewRepository.saveAllAndFlush([
                    Review.builder()
                            .productId(product.id)
                            .reviewerName("user1")
                            .reviewerRating(5)
                            .reviewText(largeReviewText)
                            .isVerifiedPurchase(true)
                            .build(),
                    Review.builder()
                            .productId(product.id)
                            .reviewerName("user2")
                            .reviewerRating(4)
                            .reviewText(largeReviewText)
                            .isVerifiedPurchase(true)
                            .build(),
                    Review.builder()
                            .productId(product.id)
                            .reviewerName("user3")
                            .reviewerRating(3)
                            .reviewText(largeReviewText)
                            .isVerifiedPurchase(false)
                            .build()
            ])

        and: "The summarizer returns chunk summaries and a final summary"
            reviewSummarizer.summarizeChunk(_, _, _) >> new ReviewSummary(
                    "Chunk summary",
                    ["Pro from chunk"],
                    ["Con from chunk"],
                    "Mixed"
            )

            reviewSummarizer.summarizeFinal(_, _, _) >> new ReviewSummary(
                    "Final merged summary across all chunks.",
                    ["Top pro 1", "Top pro 2"],
                    ["Top con 1"],
                    "Positive"
            )

        when: "We call the summarize endpoint"
            def response = webActor.summarizeReviews(product.id)

        then: "The response is 200 with the final merged summary"
            response.expectStatus().isOk()

            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "summary": "Final merged summary across all chunks.",
                    "pros": ["Top pro 1", "Top pro 2"],
                    "cons": ["Top con 1"],
                    "sentiment": "Positive"
                }
            """, body, JSONCompareMode.STRICT)
    }

    def "Product not found returns error"() {
        given: "A non-existing product ID"
            Long nonExistingId = 99999L

        when: "We call the summarize endpoint"
            def response = webActor.summarizeReviews(nonExistingId)

        then: "The response is 500 with an error message"
            response.expectStatus().is5xxServerError()

            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "status": 500,
                    "method": "POST",
                    "errors": ["Internal server error"],
                    "path": "/reviews/${nonExistingId}/summarize"
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "Reviews not parsed returns null"() {
        given: "A product with reviewsParsed=false"
            Product product = productRepository.saveAndFlush(Product.builder()
                    .title("Unparsed Product")
                    .url("http://example.com/unparsed")
                    .description("Test product")
                    .price(99.99)
                    .rating(3.0)
                    .reviewsParsed(false)
                    .build())

        when: "We call the summarize endpoint"
            def response = webActor.summarizeReviews(product.id)

        then: "The response is 200 with empty body"
            response.expectStatus().isOk()
            response.expectBody().isEmpty()
    }

    def "No reviews with text returns null"() {
        given: "A product with reviewsParsed=true but no reviews in the database"
            Product product = productRepository.saveAndFlush(Product.builder()
                    .title("No Reviews Product")
                    .url("http://example.com/no-reviews")
                    .description("Test product")
                    .price(99.99)
                    .rating(3.0)
                    .reviewsParsed(true)
                    .build())

        when: "We call the summarize endpoint"
            def response = webActor.summarizeReviews(product.id)

        then: "The response is 200 with empty body"
            response.expectStatus().isOk()
            response.expectBody().isEmpty()
    }
}
