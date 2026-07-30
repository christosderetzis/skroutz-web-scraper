package org.skroutz.scraper.skroutzwebscraper.specs.review

import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.agent.ReviewSummarizer
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.dto.ReviewSummaryDto
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product
import org.skroutz.scraper.skroutzwebscraper.review.domain.entity.Review
import org.skroutz.scraper.skroutzwebscraper.review.domain.entity.ReviewSummary
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
            reviewSummarizer.summarizeChunk(_, _, _) >> new ReviewSummaryDto(
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

        and: "the summary is saved in the database"
            def savedSummary = reviewSummaryRepository.findByProductId(product.id).orElse(null)
            with(savedSummary) {
                summary == "Excellent mid-range phone with great battery."
                pros == ["Battery life", "Value for money"].toArray()
                cons == ["Camera in low light"].toArray()
                sentiment == "Positive"
                productId == product.id
            }
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
                            .helpfulVotes(5)
                            .totalVotes(6)
                            .pros(["Great battery life", "Fast performance"] as String[])
                            .cons(["Camera quality could be better"] as String[])
                            .build(),
                    Review.builder()
                            .productId(product.id)
                            .reviewerName("user2")
                            .reviewerRating(4)
                            .reviewText(largeReviewText)
                            .isVerifiedPurchase(true)
                            .helpfulVotes(0)
                            .totalVotes(0)
                            .pros(["Good value for money"] as String[])
                            .cons(["Screen brightness is low"] as String[])
                            .build(),
                    Review.builder()
                            .productId(product.id)
                            .reviewerName("user3")
                            .reviewerRating(3)
                            .reviewText(largeReviewText)
                            .isVerifiedPurchase(false)
                            .helpfulVotes(2)
                            .totalVotes(5)
                            .pros(["Decent performance"] as String[])
                            .cons(["Average camera", "Battery drains fast"] as String[])
                            .build()
            ])

        and: "The summarizer returns chunk summaries and a final summary"
            reviewSummarizer.summarizeChunk(_, _, _) >> new ReviewSummaryDto(
                    "Chunk summary",
                    ["Pro from chunk"],
                    ["Con from chunk"],
                    "Mixed"
            )

            reviewSummarizer.summarizeFinal(_, _, _) >> new ReviewSummaryDto(
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

        and: "the final summary is saved in the database"
            def savedSummary = reviewSummaryRepository.findByProductId(product.id).orElse(null)
            with(savedSummary) {
                summary == "Final merged summary across all chunks."
                pros == ["Top pro 1", "Top pro 2"].toArray()
                cons == ["Top con 1"].toArray()
                sentiment == "Positive"
                productId == product.id
            }
    }

    def "Product not found returns error"() {
        given: "A non-existing product ID"
            Long nonExistingId = 99999L

        when: "We call the summarize endpoint"
            def response = webActor.summarizeReviews(nonExistingId)

        then: "The response is 404 with an error message"
            response.expectStatus().isNotFound()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "status": 404,
                    "method": "POST",
                    "errors": ["Product not found with id: ${nonExistingId}"],
                    "path": "/products/${nonExistingId}/reviews/summarize"
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "Reviews not parsed returns error"() {
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

        then: "The response is 400 with an error message"
            response.expectStatus().isBadRequest()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "status": 400,
                    "method": "POST",
                    "errors": ["Cannot summarize reviews for product ID ${product.id}"],
                    "path": "/products/${product.id}/reviews/summarize"
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "No reviews with text returns error"() {
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

        then: "The response is 400 with an error message"
            response.expectStatus().isBadRequest()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                    {
                        "status": 400,
                        "method": "POST",
                        "errors": ["No reviews with text found for product ID ${product.id}"],
                        "path": "/products/${product.id}/reviews/summarize"
                    }
                """, body, JSONCompareMode.LENIENT)
    }

    def "Summarization is returned for a given product if it exists in the database"() {
        given: "A product with an existing summary in the database"
            Product product = productRepository.saveAndFlush(Product.builder()
                    .title("Existing Summary Product")
                    .url("http://example.com/existing-summary")
                    .description("Test product")
                    .price(99.99)
                    .rating(3.0)
                    .reviewsParsed(true)
                    .build())

            reviewSummaryRepository.saveAndFlush(ReviewSummary.builder()
                    .productId(product.id)
                    .summary("Existing summary")
                    .pros(["Existing pro"] as String[])
                    .cons(["Existing con"] as String[])
                    .sentiment("Neutral")
                    .build())

        when: "We call the summarize endpoint"
            def response = webActor.summarizeReviews(product.id)

        then: "The response is 200 with the existing summary"
            response.expectStatus().isOk()

            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "summary": "Existing summary",
                    "pros": ["Existing pro"],
                    "cons": ["Existing con"],
                    "sentiment": "Neutral"
                }
            """, body, JSONCompareMode.STRICT)
    }
}
