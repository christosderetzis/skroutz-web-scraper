package org.skroutz.scraper.skroutzwebscraper.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Skroutz Web Scraper API")
                        .version("1.0.0")
                        .description("API for scraping and managing Skroutz product data")
                        .contact(new Contact()
                                .name("Skroutz Scraper Team")
                                .email("support@skroutz-scraper.com")));
    }
}