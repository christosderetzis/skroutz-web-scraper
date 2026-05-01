package org.skroutz.scraper.skroutzwebscraper.service;

import dev.langchain4j.internal.Json;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.agent.ReviewSummarizer;
import org.skroutz.scraper.skroutzwebscraper.dto.ReviewSummary;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.entity.Review;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.repository.ReviewRepository;
import org.skroutz.scraper.skroutzwebscraper.utils.ReviewChunker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReviewsSummarizationService {

    private final int chunkSize;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewSummarizer reviewSummarizer;

    public ReviewsSummarizationService(
            @Value("${ai.summarization.chunk-size}") int chunkSize,
            ProductRepository productRepository,
            ReviewRepository reviewRepository,
            ReviewSummarizer reviewSummarizer) {
        this.chunkSize = chunkSize;
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.reviewSummarizer = reviewSummarizer;
    }

    public ReviewSummary summarizeReviews(Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Product not found with ID: " + productId));

        if (!Boolean.TRUE.equals(product.getReviewsParsed())) {
            log.warn("Cannot summarize reviews for product ID {} because reviews have not been parsed yet.", productId);
            return null;
        }

        List<Review> reviews = reviewRepository.findAllByProductIdAndReviewTextIsNotNull(productId);

        if (reviews.isEmpty()) {
            log.info("No reviews with text found for product ID {}. Skipping summarization.", productId);
            return null;
        }

        // Filter out reviews with empty or null text and format them for the LLM
        List<String> formattedReviews = reviews.stream()
                .filter(review -> review.getReviewText() != null && !review.getReviewText().isBlank())
                .map(this::formatReviewForLlm)
                .filter(review -> !review.isBlank())
                .toList();

        // Chunk reviews into smaller pieces to fit within LLM context limits
        List<String> chunks = ReviewChunker.chunkByCharSize(formattedReviews, chunkSize);

        log.info(
                "Summarizing product ID {} ({}): {} reviews → {} chunks",
                productId,
                product.getTitle(),
                reviews.size(),
                chunks.size()
        );

        // If there's only one chunk, we can skip the final summarization step and return the summary directly
        if (chunks.size() == 1) {

            ReviewSummary summary = reviewSummarizer.summarizeChunk(
                    chunks.getFirst(),
                    safe(product.getTitle()),
                    safe(product.getDescription())
            );

            log.info("Single chunk summary for product ID {}: {}", productId, Json.toJson(summary));

            return summary;
        }

        // Summarize each chunk individually
        List<ReviewSummary> chunkSummaries = chunks.stream()
                .map(chunk -> reviewSummarizer.summarizeChunk(
                        chunk,
                        safe(product.getTitle()),
                        safe(product.getDescription())
                ))
                .toList();

        // Combine chunk summaries into a final summary
        String finalInput = chunkSummaries.stream()
                .map(this::formatChunkSummaryForFinalPass)
                .collect(Collectors.joining("\n---\n"));

        // Summarize the chunk summaries into a final review report
        ReviewSummary finalSummary = reviewSummarizer.summarizeFinal(
                finalInput,
                safe(product.getTitle()),
                safe(product.getDescription())
        );

        log.info("Final summary for product ID {}: {}", productId, Json.toJson(finalSummary));

        return finalSummary;
    }


    private String formatReviewForLlm(Review review) {

        String helpfulness = "N/A";

        if (review.getHelpfulVotes() != null
                && review.getTotalVotes() != null
                && review.getTotalVotes() > 0) {

            helpfulness = review.getHelpfulVotes() + "/" + review.getTotalVotes();
        }

        return """
                Rating: %s/5
                Verified Purchase: %s
                Helpfulness: %s
                Pros: %s
                Cons: %s
                Neutral: %s
                Review:
                %s
                """.formatted(
                safe(review.getReviewerRating()),
                Boolean.TRUE.equals(review.getIsVerifiedPurchase()) ? "Yes" : "No",
                helpfulness,
                joinArray(review.getPros()),
                joinArray(review.getCons()),
                joinArray(review.getNeutral()),
                safe(review.getReviewText())
        );
    }

    private String formatChunkSummaryForFinalPass(ReviewSummary summary) {
        return """
                Summary: %s
                Pros: %s
                Cons: %s
                Sentiment: %s
                """.formatted(
                safe(summary.summary()),
                summary.pros() == null ? "None" : String.join(", ", summary.pros()),
                summary.cons() == null ? "None" : String.join(", ", summary.cons()),
                safe(summary.sentiment())
        );
    }

    private String joinArray(String[] values) {
        return values == null || values.length == 0
                ? "None"
                : Arrays.stream(values)
                .filter(Objects::nonNull)
                .filter(v -> !v.isBlank())
                .collect(Collectors.joining(", "));
    }

    private String safe(Object value) {
        return value == null ? "N/A" : value.toString();
    }
}
