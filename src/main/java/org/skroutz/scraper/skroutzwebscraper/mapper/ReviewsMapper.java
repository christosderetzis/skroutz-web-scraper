package org.skroutz.scraper.skroutzwebscraper.mapper;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.skroutz.scraper.skroutzwebscraper.dto.ReviewsApiResponseDto;
import org.skroutz.scraper.skroutzwebscraper.entity.Review;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

@Component
@Slf4j
public class ReviewsMapper {

    private static final int PARALLEL_THRESHOLD = 50;

    public List<Review> mapToReviews(List<ReviewsApiResponseDto.ReviewDto> reviewDtos) {
        Stream<ReviewsApiResponseDto.ReviewDto> stream = reviewDtos.size() > PARALLEL_THRESHOLD
                ? reviewDtos.parallelStream()
                : reviewDtos.stream();

        return stream
                .map(this::mapToReview)
                .toList();
    }

    private Review mapToReview(ReviewsApiResponseDto.ReviewDto reviewDto) {
        String reviewText = reviewDto.getTranslatedFormattedReview() != null && !reviewDto.getTranslatedFormattedReview().isBlank()
                ? reviewDto.getTranslatedFormattedReview()
                : reviewDto.getOriginalFormattedReview();
        String[] pros = extractListItems(reviewDto.getAggregatedReviewData(), "pros");
        String[] cons = extractListItems(reviewDto.getAggregatedReviewData(), "cons");
        String[] neutral = extractListItems(reviewDto.getAggregatedReviewData(), "so-so");
        String extractedText = extractText(reviewText);
        HelperVotes helperVotes = parseHelpfulVotes(reviewDto.getHelpfulnessMessage());

        return Review.builder()
                .reviewerName(reviewDto.getAuthorName())
                .reviewText(extractedText != null && !extractedText.isBlank() ? extractedText : null)
                .isVerifiedPurchase(reviewDto.getVerified() != null && reviewDto.getVerified())
                .reviewerRating(reviewDto.getRating())
                .reviewDate(parseDateText(reviewDto.getReviewTime()))
                .cons(cons.length > 0 ? cons : null)
                .pros(pros.length > 0 ? pros : null)
                .neutral(neutral.length > 0 ? neutral : null)
                .helpfulVotes(helperVotes.helpfulVotes())
                .totalVotes(helperVotes.totalVotes())
                .build();
    }

    private String[] extractListItems(String escapedHtml, String className) {
        if (escapedHtml == null) {
            return new String[0];
        }
        Document doc = Jsoup.parse(escapedHtml);

        return doc.select("ul." + className + " li")
                .stream()
                .map(Element::text)
                .toArray(String[]::new);
    }

    private String extractText(String html) {
        if (html == null) {
            return null;
        }
        return Jsoup.parse(html).text();
    }

    private LocalDate parseDateText(String dateText) {
        try {
            if (dateText.contains("/")) {
                String[] parts = dateText.split("/");
                return LocalDate.of(
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[0])
                );
            } else if (dateText.contains("-")) {
                String[] parts = dateText.split("-");
                return LocalDate.of(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2])
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

    private record HelperVotes(Integer helpfulVotes, Integer totalVotes) {
    }
}
