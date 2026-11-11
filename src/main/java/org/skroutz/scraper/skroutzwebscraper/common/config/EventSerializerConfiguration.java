package org.skroutz.scraper.skroutzwebscraper.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.core.EventSerializer;

@Configuration
class EventSerializerConfiguration {

    @Bean
    public EventSerializer eventSerializer(ObjectMapper objectMapper) {
        return new EventSerializer() {
            @Override
            public Object serialize(Object event) {
                try {
                    // Convert domain event object to JSON string for the database text column
                    return objectMapper.writeValueAsString(event);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to serialize event: " + event, e);
                }
            }

            @Override
            public <T> T deserialize(Object serialized, Class<T> type) {
                try {
                    // Read JSON string back into the target event class type
                    return objectMapper.readValue(serialized.toString(), type);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to deserialize event into " + type.getName(), e);
                }
            }
        };
    }
}
