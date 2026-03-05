package org.skroutz.scraper.skroutzwebscraper.config;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class SeleniumConfig {

    @Value("${scraper.headless:true}")
    private boolean headless;

    @Value("${scraper.selenium.url:}")
    private String seleniumUrl;

    @Bean
    @Scope("prototype")
    public WebDriver webDriver() {
        ChromeOptions options = chromeOptions();

        try {
            WebDriver driver;

            if (seleniumUrl != null && !seleniumUrl.isBlank()) {
                driver = new RemoteWebDriver(new URL(seleniumUrl), options);
            } else {
                WebDriverManager.chromedriver().setup();
                driver = new ChromeDriver(options);
            }

            return driver;

        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid Selenium URL: " + seleniumUrl, e);
        }
    }

    private ChromeOptions chromeOptions() {
        ChromeOptions options = new ChromeOptions();

        if (headless) {
            options.addArguments("--headless=new");
        }

        options.addArguments(
                "--incognito",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1920,1080"
        );

        options.addArguments(
                "user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
        );

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("profile.managed_default_content_settings.images", 2);
        options.setExperimentalOption("prefs", prefs);

        return options;
    }
}