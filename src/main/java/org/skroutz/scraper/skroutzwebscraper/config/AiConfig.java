package org.skroutz.scraper.skroutzwebscraper.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.skroutz.scraper.skroutzwebscraper.agent.ReviewSummarizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AiConfig {

    @Bean
    public ChatModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl("http://192.162.1.137:1234/v1")
                .apiKey("lm-studio")
                .modelName("local-model")
                .temperature(0.2)
                .maxTokens(300)
                .timeout(Duration.ofMinutes(2))
                .build();
    }

    @Bean
    public ReviewSummarizer reviewSummarizer(ChatModel model) {
        return AiServices.create(ReviewSummarizer.class, model);
    }
}
