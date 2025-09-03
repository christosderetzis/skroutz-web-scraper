package org.skroutz.scraper.skroutzwebscraper.utils.config

import org.openqa.selenium.WebDriver
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Scope

@TestConfiguration
class TestSeleniumConfig {

    @Bean
    @Scope("prototype")
    WebDriver webDriver() {
        return new TestWebDriverProvider().createWebDriver()
    }
}
