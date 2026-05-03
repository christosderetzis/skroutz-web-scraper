package org.skroutz.scraper.skroutzwebscraper.config;

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.skroutz.scraper.skroutzwebscraper.agent.ReviewSummarizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AiConfig {

    @Value("${ai.openai.base-url}")
    private String baseUrl;

    @Value("${ai.openai.model}")
    private String modelName;

    @Value("${ai.openai.max-tokens:16000}")
    private int maxTokens;

    @Bean
    public OpenAiChatModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .httpClientBuilder(new JdkHttpClientBuilder()
                        .httpClientBuilder(HttpClient.newBuilder()
                                .version(HttpClient.Version.HTTP_1_1)))
                .baseUrl(baseUrl)
                .apiKey("dummy-key")
                .modelName(modelName)
                .timeout(Duration.ofMinutes(5))
                .maxTokens(maxTokens)
                .temperature(0.3)
                .logResponses(true)
                .build();
    }

    @Bean
    public ReviewSummarizer reviewSummarizer(ChatModel model) {
        return AiServices.create(ReviewSummarizer.class, model);
    }
}
