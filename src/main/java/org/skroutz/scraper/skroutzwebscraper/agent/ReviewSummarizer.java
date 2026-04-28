package org.skroutz.scraper.skroutzwebscraper.agent;

import dev.langchain4j.service.UserMessage;
import org.skroutz.scraper.skroutzwebscraper.dto.PartialSummary;

public interface ReviewSummarizer {

    @UserMessage("""
        Analyze these reviews.

        STRICT RULES:
        - summary: max 120 words
        - pros: max 5 items
        - cons: max 5 items
        - sentiment: Positive, Mixed, or Negative
        - Return ONLY valid JSON

        Reviews:
        {{input}}
        """)
    PartialSummary summarize(String input);
}
