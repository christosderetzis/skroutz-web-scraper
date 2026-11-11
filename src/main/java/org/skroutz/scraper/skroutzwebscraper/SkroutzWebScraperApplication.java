package org.skroutz.scraper.skroutzwebscraper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication
@EntityScan(basePackages = {
        "org.skroutz.scraper.skroutzwebscraper",
        "org.springframework.modulith.events.jpa"
})
public class SkroutzWebScraperApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkroutzWebScraperApplication.class, args);
    }

}
