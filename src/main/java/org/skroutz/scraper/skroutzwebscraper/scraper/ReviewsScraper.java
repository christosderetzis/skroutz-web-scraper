package org.skroutz.scraper.skroutzwebscraper.scraper;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.LocalDate;

import org.skroutz.scraper.skroutzwebscraper.dto.ReviewsApiResponseDto;
import org.skroutz.scraper.skroutzwebscraper.entity.Review;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Component
public class ReviewsScraper extends AbstractScraper {

    private static final int PARALLEL_THRESHOLD = 50;
    private final WebClient webClient;

    public ReviewsScraper(ApplicationContext applicationContext, WebClient webClient) {
        super(applicationContext);
        this.webClient = webClient;
    }

    public List<Review> scrapeReviews(String url) throws InterruptedException {
        log.info("Scraping reviews for {}", url);

        Integer offset = 0;
        Integer pageSize;
        List<ReviewsApiResponseDto.ReviewDto> reviewDtos = new ArrayList<>();
        String reviewUrl = buildReviewUrl(url, offset);

        do {
            Thread.sleep(100); // Sleep for 1 second between page requests to avoid overwhelming the server
            ReviewsApiResponseDto response = fetchReviewPage(reviewUrl);
            List<ReviewsApiResponseDto.ReviewDto> reviewPageItems = response.getReviews().getReviews();
            reviewDtos.addAll(reviewPageItems);
            pageSize = reviewPageItems.size();
            offset += pageSize;
            reviewUrl = buildReviewUrl(url, offset);
        } while (pageSize > 0);

        log.info("Total reviews fetched: {}", reviewDtos.size());
        return extractReviews(reviewDtos);
    }

    public ReviewsApiResponseDto fetchReviewPage(String url) {
        log.debug("Fetching single review data from URL: {}", url);

        try {
            ReviewsApiResponseDto reviewsApiResponseDto = webClient
                    .get()
                    .uri(url)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                log.error("Error fetching review data. Status: {}", clientResponse.statusCode());
                                return Mono.error(new RuntimeException(
                                        "Failed to fetch review data from API"
                                ));
                            }
                    )
                    .bodyToMono(ReviewsApiResponseDto.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (reviewsApiResponseDto == null) {
                log.error("Received null response from review API");
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to fetch review data: : null response"
                );
            }

            log.debug("Successfully fetched review data from URL: {}", url);
            return reviewsApiResponseDto;
        } catch (Exception e) {
            log.error("Error fetching review data from URL: {}", url, e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch review data: " + e.getMessage(),
                    e
            );
        }
    }

    private String buildReviewUrl(String productUrl, Integer offset) {
        int htmlIndex = productUrl.indexOf(".html");
        if (htmlIndex != -1) {
            productUrl = productUrl.substring(0, htmlIndex);
        }
        return "%s/reviews.json?offset=%d".formatted(productUrl, offset);
    }

    private List<Review> extractReviews(List<ReviewsApiResponseDto.ReviewDto> reviewElements) {
        // Use parallel streams only for large datasets (>50 reviews)
        Stream<ReviewsApiResponseDto.ReviewDto> stream = reviewElements.size() > PARALLEL_THRESHOLD
                ? reviewElements.parallelStream()
                : reviewElements.stream();

        return stream
                .map(this::parseReview)
                .toList();
    }

    private Review parseReview(ReviewsApiResponseDto.ReviewDto reviewDto) {
        String[] pros = extractListItems(reviewDto.getAggregatedReviewData(), "pros");
        String[] cons = extractListItems(reviewDto.getAggregatedReviewData(), "cons");
        String[] neutral = extractListItems(reviewDto.getAggregatedReviewData(), "so-so");
        String extractedText = extractText(reviewDto.getOriginalFormattedReview());
        HelperVotes helperVotes = parseHelpfulVotes(reviewDto.getHelpfulnessMessage());

        Review review = new Review();
        review.setReviewerName(reviewDto.getAuthorName());
        review.setReviewText(extractedText != null && !extractedText.isBlank() ? extractedText : null);
        review.setIsVerifiedPurchase(reviewDto.getVerified() != null && reviewDto.getVerified());
        review.setReviewerRating(reviewDto.getRating());
        review.setReviewDate(parseDateText(reviewDto.getReviewTime()));
        review.setCons(cons.length > 0 ? cons : null);
        review.setPros(pros.length > 0 ? pros : null);
        review.setNeutral(neutral.length > 0 ? neutral : null);
        review.setHelpfulVotes(helperVotes.helpfulVotes());
        review.setTotalVotes(helperVotes.totalVotes());
        return review;
    }

    /**
     * Converts a list to String array, returns null for empty lists
     */
    private String[] extractListItems(String escapedHtml, String className) {
        if (escapedHtml == null) {
            return new String[0];
        }
        String html = StringEscapeUtils.unescapeJava(escapedHtml);
        Document doc = Jsoup.parse(html);

        return doc.select("ul." + className + " li")
                .stream()
                .map(Element::text)
                .toArray(String[]::new);
    }

    public String extractText(String html) {
        if (html == null) {
            return null;
        }
        return Jsoup.parse(html).text();
    }

    /**
     * Parses date text in format "23/09/2023" or "2025-09-23"
     */
    private LocalDate parseDateText(String dateText) {
        try {
            if (dateText.contains("/")) {
                String[] parts = dateText.split("/");
                return LocalDate.of(
                        Integer.parseInt(parts[2]), // year
                        Integer.parseInt(parts[1]), // month
                        Integer.parseInt(parts[0])  // day
                );
            } else if (dateText.contains("-")) {
                String[] parts = dateText.split("-");
                return LocalDate.of(
                        Integer.parseInt(parts[0]), // year
                        Integer.parseInt(parts[1]), // month
                        Integer.parseInt(parts[2])  // day
                );
            } else {
                log.warn("Unexpected date format: {}", dateText);
                return null;
            }
        } catch (Exception e) {
            log.warn("Failed to parse review date '{}': {}", dateText, e.getMessage());
            return null;
        }
    }

    /**
     * Parses helpful votes text like "1 out of 1 found this review helpful"
     * or "1 στους 1 χρήστης βρήκε αυτή την κριτική χρήσιμη"
     */
    private HelperVotes parseHelpfulVotes(String helpfulText) {
        HelperVotes votes = new HelperVotes(0, 0);
        if (helpfulText == null) {
            return votes;
        }

        try {
            String[] parts = helpfulText.split(" ");
            if (helpfulText.contains("out of")) {
                votes = new HelperVotes(Integer.parseInt(parts[0]), Integer.parseInt(parts[3]));
            } else if (helpfulText.contains("χρήστες") || helpfulText.contains("στους")) {
                votes = new HelperVotes(Integer.parseInt(parts[0]), Integer.parseInt(parts[2]));
            } else {
                log.warn("Unexpected helpful votes format: {}", helpfulText);
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            log.warn("Failed to parse helpful votes '{}': {}", helpfulText, e.getMessage());
        }
        return votes;
    }

    public record HelperVotes(Integer helpfulVotes, Integer totalVotes) {
    }
}
