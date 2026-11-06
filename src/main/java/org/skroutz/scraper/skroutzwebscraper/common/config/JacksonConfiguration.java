package org.skroutz.scraper.skroutzwebscraper.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class JacksonConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // Recommended: Register JavaTimeModule to handle modern Java dates (LocalDate, LocalDateTime)
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }
}
