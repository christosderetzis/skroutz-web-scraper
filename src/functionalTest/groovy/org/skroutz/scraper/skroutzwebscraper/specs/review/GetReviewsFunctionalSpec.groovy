package org.skroutz.scraper.skroutzwebscraper.specs.review

import org.junit.Ignore
import org.skroutz.scraper.skroutzwebscraper.common.dto.PagedResponse
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product
import org.skroutz.scraper.skroutzwebscraper.review.domain.entity.Review
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.dto.ReviewResponseDto
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec
import org.skroutz.scraper.skroutzwebscraper.utils.creators.ReviewCreator
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.springframework.core.ParameterizedTypeReference

import java.time.LocalDate

class GetReviewsFunctionalSpec extends BaseFunctionalSpec {

    Product product
    Review reviewHighVotes
    Review reviewMidVotes
    Review reviewTieOld
    Review reviewTieNew
    Review reviewTieSameDateLowId
    Review reviewTieSameDateHighId

    private void setupStandardReviewDataset() {
        product = createAndIndexProduct("Test Product")

        reviewHighVotes = reviewRepository.saveAndFlush(
                ReviewCreator.createReview(product.id, "Reviewer A", 5, LocalDate.of(2024, 1, 1), 10, 12, "Great product!"))

        reviewMidVotes = reviewRepository.saveAndFlush(
                ReviewCreator.createReview(product.id, "Reviewer B", 4, LocalDate.of(2024, 3, 1), 7, 10, "Good product."))

        reviewTieOld = reviewRepository.saveAndFlush(
                ReviewCreator.createReview(product.id, "Reviewer C", 3, LocalDate.of(2024, 2, 1), 5, 8, "Okay product."))

        reviewTieNew = reviewRepository.saveAndFlush(
                ReviewCreator.createReview(product.id, "Reviewer D", 4, LocalDate.of(2024, 6, 15), 5, 8, "Decent product."))

        reviewTieSameDateLowId = reviewRepository.saveAndFlush(
                ReviewCreator.createReview(product.id, "Reviewer E", 3, LocalDate.of(2024, 6, 1), 5, 8, "Same date lower ID."))

        reviewTieSameDateHighId = reviewRepository.saveAndFlush(
                ReviewCreator.createReview(product.id, "Reviewer F", 5, LocalDate.of(2024, 6, 1), 5, 8, "Same date higher ID."))
    }

    def "Get reviews sorted by helpful votes with recency and ID tie-breakers"() {
        given: "a product with reviews having varied helpful votes and dates"
            setupStandardReviewDataset()

        when: "requesting reviews sorted by helpful votes"
            def response = webActor.getProductReviews(product.id, "helpful")

        then: "the API returns 200 OK"
            response.expectStatus().isOk()

        and: "reviews are ordered by helpful votes DESC, then review date DESC, then ID DESC"
            String body = response
                    .expectBody(String)
                    .returnResult()
                    .getResponseBody()

            def reviewsJsonArray = """
                    {
                        "id": ${reviewHighVotes.id},
                        "reviewerName": "${reviewHighVotes.reviewerName}",
                        "reviewerRating": ${reviewHighVotes.reviewerRating},
                        "reviewDate": "${reviewHighVotes.reviewDate}",
                        "helpfulVotes": ${reviewHighVotes.helpfulVotes},
                        "totalVotes": ${reviewHighVotes.totalVotes},
                        "reviewText": "${reviewHighVotes.reviewText}"
                    },
                    {
                        "id": ${reviewMidVotes.id},
                        "reviewerName": "${reviewMidVotes.reviewerName}",
                        "reviewerRating": ${reviewMidVotes.reviewerRating},
                        "reviewDate": "${reviewMidVotes.reviewDate}",
                        "helpfulVotes": ${reviewMidVotes.helpfulVotes},
                        "totalVotes": ${reviewMidVotes.totalVotes},
                        "reviewText": "${reviewMidVotes.reviewText}"
                    },
                    {
                        "id": ${reviewTieNew.id},
                        "reviewerName": "${reviewTieNew.reviewerName}",
                        "reviewerRating": ${reviewTieNew.reviewerRating},
                        "reviewDate": "${reviewTieNew.reviewDate}",
                        "helpfulVotes": ${reviewTieNew.helpfulVotes},
                        "totalVotes": ${reviewTieNew.totalVotes},
                        "reviewText": "${reviewTieNew.reviewText}"
                    },
                    {
                        "id": ${reviewTieSameDateHighId.id},
                        "reviewerName": "${reviewTieSameDateHighId.reviewerName}",
                        "reviewerRating": ${reviewTieSameDateHighId.reviewerRating},
                        "reviewDate": "${reviewTieSameDateHighId.reviewDate}",
                        "helpfulVotes": ${reviewTieSameDateHighId.helpfulVotes},
                        "totalVotes": ${reviewTieSameDateHighId.totalVotes},
                        "reviewText": "${reviewTieSameDateHighId.reviewText}"
                    },
                    {
                        "id": ${reviewTieSameDateLowId.id},
                        "reviewerName": "${reviewTieSameDateLowId.reviewerName}",
                        "reviewerRating": ${reviewTieSameDateLowId.reviewerRating},
                        "reviewDate": "${reviewTieSameDateLowId.reviewDate}",
                        "helpfulVotes": ${reviewTieSameDateLowId.helpfulVotes},
                        "totalVotes": ${reviewTieSameDateLowId.totalVotes},
                        "reviewText": "${reviewTieSameDateLowId.reviewText}"
                    },
                    {
                        "id": ${reviewTieOld.id},
                        "reviewerName": "${reviewTieOld.reviewerName}",
                        "reviewerRating": ${reviewTieOld.reviewerRating},
                        "reviewDate": "${reviewTieOld.reviewDate}",
                        "helpfulVotes": ${reviewTieOld.helpfulVotes},
                        "totalVotes": ${reviewTieOld.totalVotes},
                        "reviewText": "${reviewTieOld.reviewText}"
                    }
                    
                    """
            def expectedBody = """
                    {
                        "content": [ ${reviewsJsonArray} ],
                        "metadata": {
                            "pageNumber": 0,
                            "pageSize": 10,
                            "totalElements": 6,
                            "totalPages": 1,
                            "isLast": true,
                            "isFirst": true
                        }
                    }
                    """

            JSONAssert.assertEquals(expectedBody, body, JSONCompareMode.STRICT_ORDER)
    }

    def "Get reviews sorted by recency, ignoring helpful votes"() {
        given: "a product with reviews having varied helpful votes and dates"
            setupStandardReviewDataset()

        when: "requesting reviews sorted by recency"
            def response = webActor.getProductReviews(product.id, "recent")

        then: "the API returns 200 OK"
            response.expectStatus().isOk()

        and: "reviews are ordered strictly by review_date DESC (newest first), ignoring helpful votes"
            String body = response
                    .expectBody(String)
                    .returnResult()
                    .getResponseBody()

            def reviewsJsonArray = """
                    {
                        "id": ${reviewTieNew.id},
                        "reviewerName": "${reviewTieNew.reviewerName}",
                        "reviewerRating": ${reviewTieNew.reviewerRating},
                        "reviewDate": "${reviewTieNew.reviewDate}",
                        "helpfulVotes": ${reviewTieNew.helpfulVotes},
                        "totalVotes": ${reviewTieNew.totalVotes},
                        "reviewText": "${reviewTieNew.reviewText}"
                    },
                    {
                        "id": ${reviewTieSameDateHighId.id},
                        "reviewerName": "${reviewTieSameDateHighId.reviewerName}",
                        "reviewerRating": ${reviewTieSameDateHighId.reviewerRating},
                        "reviewDate": "${reviewTieSameDateHighId.reviewDate}",
                        "helpfulVotes": ${reviewTieSameDateHighId.helpfulVotes},
                        "totalVotes": ${reviewTieSameDateHighId.totalVotes},
                        "reviewText": "${reviewTieSameDateHighId.reviewText}"
                    },
                    {
                        "id": ${reviewTieSameDateLowId.id},
                        "reviewerName": "${reviewTieSameDateLowId.reviewerName}",
                        "reviewerRating": ${reviewTieSameDateLowId.reviewerRating},
                        "reviewDate": "${reviewTieSameDateLowId.reviewDate}",
                        "helpfulVotes": ${reviewTieSameDateLowId.helpfulVotes},
                        "totalVotes": ${reviewTieSameDateLowId.totalVotes},
                        "reviewText": "${reviewTieSameDateLowId.reviewText}"
                    },
                    {
                        "id": ${reviewMidVotes.id},
                        "reviewerName": "${reviewMidVotes.reviewerName}",
                        "reviewerRating": ${reviewMidVotes.reviewerRating},
                        "reviewDate": "${reviewMidVotes.reviewDate}",
                        "helpfulVotes": ${reviewMidVotes.helpfulVotes},
                        "totalVotes": ${reviewMidVotes.totalVotes},
                        "reviewText": "${reviewMidVotes.reviewText}"
                    },
                    {
                        "id": ${reviewTieOld.id},
                        "reviewerName": "${reviewTieOld.reviewerName}",
                        "reviewerRating": ${reviewTieOld.reviewerRating},
                        "reviewDate": "${reviewTieOld.reviewDate}",
                        "helpfulVotes": ${reviewTieOld.helpfulVotes},
                        "totalVotes": ${reviewTieOld.totalVotes},
                        "reviewText": "${reviewTieOld.reviewText}"
                    },
                    {
                        "id": ${reviewHighVotes.id},
                        "reviewerName": "${reviewHighVotes.reviewerName}",
                        "reviewerRating": ${reviewHighVotes.reviewerRating},
                        "reviewDate": "${reviewHighVotes.reviewDate}",
                        "helpfulVotes": ${reviewHighVotes.helpfulVotes},
                        "totalVotes": ${reviewHighVotes.totalVotes},
                        "reviewText": "${reviewHighVotes.reviewText}"
                    }
                    
                    """
            def expectedBody = """
                    {
                        "content": [ ${reviewsJsonArray} ],
                        "metadata": {
                            "pageNumber": 0,
                            "pageSize": 10,
                            "totalElements": 6,
                            "totalPages": 1,
                            "isLast": true,
                            "isFirst": true
                        }
                    }
                    """

            JSONAssert.assertEquals(expectedBody, body, JSONCompareMode.STRICT_ORDER)
    }

    @Ignore
    def "Get reviews with pagination, first page of 10 out of 15 reviews"() {
        given: "a product with 15 reviews sorted by helpful votes"
            Product paginationProduct = createAndIndexProduct("Pagination Test Product")

            // Create 15 reviews with varying helpful votes for testing pagination
            List<Review> reviewsList = []
            for (int i = 1; i <= 15; i++) {
                Review review = reviewRepository.saveAndFlush(
                        ReviewCreator.createReview(paginationProduct.id, "Reviewer ${i}", 5,
                                LocalDate.of(2024, 1, i), 15 - i, 20, "Review ${i}"))
                reviewsList.add(review)
            }

            List<Review> firstPageExpected = reviewsList.sort { -it.helpfulVotes }[0..9]
            List<Review> secondPageExpected = reviewsList.sort { -it.helpfulVotes }[10..14]

        when: "requesting first page (page=0, size=10) sorted by helpful votes"
            def response = webActor.getProductReviews(paginationProduct.id, "helpful", 0, 10)

        then: "the API returns 200 OK"
            response.expectStatus().isOk()

        and: "the response contains exactly 10 reviews on first page with correct pagination metadata"
            PagedResponse<ReviewResponseDto> firstPageResponse = response
                    .expectBody(new ParameterizedTypeReference<PagedResponse<ReviewResponseDto>>() {})
                    .returnResult()
                    .getResponseBody()

        and: "the first page contains 10 reviews and correct pagination metadata"
            with(firstPageResponse) {
                content().size() == 10
                content()*.id() == firstPageExpected*.id

                with(metadata()) {
                    pageNumber() == 0
                    pageSize() == 10
                    totalElements() == 15
                    totalPages() == 2
                    isFirst()
                    !isLast()
                }
            }

        and: "get second page (page=1, size=10) returns remaining 5 reviews"
            def secondPageResponse = webActor.getProductReviews(paginationProduct.id, "helpful", 1, 10)
                    .expectBody(new ParameterizedTypeReference<PagedResponse<ReviewResponseDto>>() {})
                    .returnResult()
                    .getResponseBody()

        and: "the second page contains 5 reviews and correct pagination metadata"
            with(secondPageResponse) {
                content().size() == 5
                content()*.id() == secondPageExpected*.id

                with(metadata()) {
                    pageNumber() == 1
                    pageSize() == 10
                    totalElements() == 15
                    totalPages() == 2
                    !isFirst()
                    isLast()
                }
            }
    }

    def "Unhappy path - Get product by non-existing id"() {
        given: "a non-existing product ID"
        Long nonExistingProductId = 9999L

        when: "requesting the product by the non-existing ID"
        def response = webActor.getProductReviews(nonExistingProductId)

        then: "the response status should be 404 Not Found"
        response.expectStatus().isNotFound()

        and: "the response body should contain an error message"
        String responseBody = response.expectBody(String).returnResult().getResponseBody()
        String expectedResponseBody = """
                {
                    "status": 404,
                    "method": "GET",
                    "errors": ["Product not found with id: ${nonExistingProductId}"],
                    "path": "/products/${nonExistingProductId}/reviews?sort=helpful"
                }
                """
        JSONAssert.assertEquals(expectedResponseBody, responseBody, JSONCompareMode.LENIENT)
    }
}
