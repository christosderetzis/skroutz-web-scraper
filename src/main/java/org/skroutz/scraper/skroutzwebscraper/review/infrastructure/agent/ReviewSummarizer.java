package org.skroutz.scraper.skroutzwebscraper.review.infrastructure.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.dto.ReviewSummaryDto;
import org.springframework.stereotype.Component;

@Component
public interface ReviewSummarizer {

    @SystemMessage("""
            You are a product review intelligence system.
    
            Your job:
            Analyze customer reviews and extract recurring patterns.
    
            Prioritize:
            - repeated themes
            - highly rated strengths
            - repeated complaints
            - verified purchase reliability
            - meaningful customer experience trends
    
            Ignore:
            - one-off niche complaints
            - irrelevant reviewer details
            - duplicate wording
    
            Be concise, factual, and structured.
            """)
    @UserMessage("""
            PRODUCT TITLE:
            {{productTitle}}
    
            PRODUCT DESCRIPTION:
            {{productDescription}}
    
            CUSTOMER REVIEWS:
            {{reviews}}
    
            STRICT OUTPUT RULES:
            - summary: maximum 100 words
            - pros: maximum 5 recurring strengths
            - cons: maximum 5 recurring weaknesses
            - sentiment: Positive, Mixed, or Negative
            - Focus only on repeated customer patterns
            - Use product context when relevant
            - Return ONLY valid JSON
            - NEVER use literal double-quote characters (") inside JSON string values. Use 'in.' or 'inch' instead of the inch symbol.
            /no_think
            """)
    ReviewSummaryDto summarizeChunk(
            @V("reviews") String reviews,
            @V("productTitle") String productTitle,
            @V("productDescription") String productDescription
    );

    @SystemMessage("""
            You are a senior product review synthesis system.
    
            Your job:
            Merge multiple chunk summaries into one final product intelligence report.
    
            Prioritize:
            - most frequent strengths
            - most frequent weaknesses
            - overall product satisfaction
            - consistency across review groups
    
            You must:
            - remove duplicates
            - rank by recurrence
            - avoid overemphasizing edge cases
            - produce final buyer-relevant conclusions
    
            Be highly concise and decisive.
            """)
    @UserMessage("""
            PRODUCT TITLE:
            {{productTitle}}
    
            PRODUCT DESCRIPTION:
            {{productDescription}}
    
            CHUNK SUMMARIES:
            {{chunkSummaries}}
    
            STRICT OUTPUT RULES:
            - summary: maximum 150 words
            - pros: maximum 5 ranked recurring strengths
            - cons: maximum 5 ranked recurring weaknesses
            - sentiment: Positive, Mixed, or Negative
            - Prioritize repeated patterns across all summaries
            - Remove duplicates
            - Return ONLY valid JSON
            - NEVER use literal double-quote characters (") inside JSON string values. Use 'in.' or 'inch' instead of the inch symbol.
            /no_think
            """)
    ReviewSummaryDto summarizeFinal(
            @V("chunkSummaries") String chunkSummaries,
            @V("productTitle") String productTitle,
            @V("productDescription") String productDescription
    );
}
