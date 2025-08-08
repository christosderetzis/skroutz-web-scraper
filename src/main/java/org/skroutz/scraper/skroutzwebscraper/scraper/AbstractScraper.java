package org.skroutz.scraper.skroutzwebscraper.scraper;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.context.ApplicationContext;

import java.util.function.Function;

@Slf4j
public abstract class AbstractScraper {

    protected final ApplicationContext applicationContext;

    public AbstractScraper(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    protected <T> T executeWithWebDriver(Function<WebDriver, T> operation, String url, String operationName) {
        WebDriver webDriver = null;
        try {
            // Get a fresh WebDriver instance for each scraping operation
            webDriver = applicationContext.getBean(WebDriver.class);
            return operation.apply(webDriver);
        } catch (Exception e) {
            log.error("Error {} from {}: {}", operationName, url, e.getMessage(), e);
            return null;
        } finally {
            // Clean up the WebDriver instance
            if (webDriver != null) {
                try {
                    webDriver.quit();
                } catch (Exception e) {
                    log.warn("Error closing WebDriver: {}", e.getMessage());
                }
            }
        }
    }
}