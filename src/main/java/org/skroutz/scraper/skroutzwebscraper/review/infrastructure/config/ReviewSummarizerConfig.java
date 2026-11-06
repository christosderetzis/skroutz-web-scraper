package org.skroutz.scraper.skroutzwebscraper.review.infrastructure.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.agent.ReviewSummarizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReviewSummarizerConfig {

    @Bean
    public ReviewSummarizer reviewSummarizer(ChatModel model) {
        return AiServices.create(ReviewSummarizer.class, model);
    }
}
