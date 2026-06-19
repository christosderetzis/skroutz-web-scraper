package org.skroutz.scraper.skroutzwebscraper.review.application.service;


import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.product.infrastructure.exception.ProductNotFoundException;
import org.skroutz.scraper.skroutzwebscraper.product.domain.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.product.application.service.ProductsService;
import org.skroutz.scraper.skroutzwebscraper.review.domain.chunker.ReviewChunker;
import org.skroutz.scraper.skroutzwebscraper.review.domain.entity.Review;
import org.skroutz.scraper.skroutzwebscraper.review.domain.entity.ReviewSummary;
import org.skroutz.scraper.skroutzwebscraper.review.domain.repository.ReviewRepository;
import org.skroutz.scraper.skroutzwebscraper.review.domain.repository.ReviewSummaryRepository;
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.dto.ReviewSummaryDto;
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.mapper.ReviewSummaryMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReviewsSummarizationService {

    private final int chunkSize;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewSummaryRepository reviewSummaryRepository;
    private final ProductsService.ReviewSummarizer reviewSummarizer;
    private final ReviewSummaryMapper reviewSummaryMapper;

    public ReviewsSummarizationService(
            @Value("${ai.summarization.chunk-size}") int chunkSize,
            ProductRepository productRepository,
            ReviewRepository reviewRepository,
            ReviewSummaryRepository reviewSummaryRepository,
            ProductsService.ReviewSummarizer reviewSummarizer,
            ReviewSummaryMapper reviewSummaryMapper){
        this.chunkSize = chunkSize;
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.reviewSummaryRepository = reviewSummaryRepository;
        this.reviewSummarizer = reviewSummarizer;
        this.reviewSummaryMapper = reviewSummaryMapper;
    }

    public ReviewSummaryDto summarizeReviews(Long productId) {

        // Fetch the product to ensure it exists and to get its title and description for better summarization context
        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new ProductNotFoundException(productId));

        if (!Boolean.TRUE.equals(product.getReviewsParsed())) {
            log.warn("Cannot summarize reviews for product ID {} because reviews have not been parsed yet.", productId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot summarize reviews for product ID " + productId);
        }

        // Check if a summarization already exists for this product and return it if found
        Optional<ReviewSummary> existingSummary = reviewSummaryRepository.findByProductId(productId);
        if (existingSummary.isPresent()) {
            log.info("Existing summarization found for product ID {}. Returning cached summary.", productId);
            return reviewSummaryMapper.toDto(existingSummary.get());
        }

        // Fetch all reviews with non-null review text for the product
        List<Review> reviews = reviewRepository.findAllByProductIdAndReviewTextIsNotNull(productId);

        if (reviews.isEmpty()) {
            log.info("No reviews with text found for product ID {}. Skipping summarization.", productId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No reviews with text found for product ID " + productId);
        }

        // Format reviews for the LLM, skipping any that produce blank output
        List<String> formattedReviews = reviews.stream()
                .filter(review -> !review.getReviewText().isBlank())
                .map(this::formatReviewForLlm)
                .filter(formatted -> !formatted.isBlank())
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

        try {
            // If there's only one chunk, we can skip the final summarization step and return the summary directly
            if (chunks.size() == 1) {
                ReviewSummaryDto summary = reviewSummarizer.summarizeChunk(
                        chunks.getFirst(),
                        safe(product.getTitle()),
                        safe(product.getDescription())
                );

                saveSummarization(productId, summary);
                return summary;
            }

            // Summarize each chunk individually
            List<ReviewSummaryDto> chunkSummaries = chunks.stream()
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
            ReviewSummaryDto finalSummary = reviewSummarizer.summarizeFinal(
                    finalInput,
                    safe(product.getTitle()),
                    safe(product.getDescription())
            );

            saveSummarization(productId, finalSummary);
            return finalSummary;
        } catch (Exception e) {
            log.error("AI summarization failed for product ID {}: {}", productId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI summarization service is unavailable. Please try again later.");
        }
    }

    private void saveSummarization(Long productId, ReviewSummaryDto summary) {
        reviewSummaryRepository.save(
                reviewSummaryMapper.toEntity(summary, productId)
        );
        log.info("Saved summarization for product ID {}", productId);
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

    private String formatChunkSummaryForFinalPass(ReviewSummaryDto summary) {
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
