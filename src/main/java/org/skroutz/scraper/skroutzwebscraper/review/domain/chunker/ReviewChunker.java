package org.skroutz.scraper.skroutzwebscraper.review.domain.chunker;

import java.util.ArrayList;
import java.util.List;

public class ReviewChunker {

    public static List<String> chunkByCharSize(List<String> reviews, int maxChars) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String review : reviews) {
            if (review == null || review.isBlank()) continue;

            if (!current.isEmpty() && current.length() + review.length() > maxChars) {
                chunks.add(current.toString());
                current = new StringBuilder();
            }

            current.append(review).append("\n---\n");
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }

        return chunks;
    }
}
