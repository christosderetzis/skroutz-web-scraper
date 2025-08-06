package org.skroutz.scraper.skroutzwebscraper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SkroutzWebScraperApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkroutzWebScraperApplication.class, args);
    }

}
