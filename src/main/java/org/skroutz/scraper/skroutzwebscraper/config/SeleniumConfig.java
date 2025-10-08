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
import java.net.URI;
import java.util.Arrays;

@Configuration
public class SeleniumConfig {

    @Value("${scraper.headless:true}")
    private boolean headless;

    @Value("${scraper.timeout:3000}")
    private int timeoutMs;

    @Value("${scraper.chromeUserDataDir:}")
    private String userDataDir;

    @Value("${scraper.selenium.url:}")
    private String seleniumUrl;

    @Bean
    @Scope("prototype")
    public WebDriver webDriver() {
        ChromeOptions options = new ChromeOptions();

        if (headless) {
            options.addArguments("--headless=new");  // Use new headless mode
        }

        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36");
        options.addArguments("--start-maximized");
        options.setExperimentalOption("excludeSwitches", Arrays.asList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        options.addArguments("--disable-blink-features=AutomationControlled");

        if (!userDataDir.isBlank()) {
            options.addArguments("user-data-dir=" + userDataDir);
        }

        WebDriverManager.chromedriver().setup();
        WebDriver driver = new ChromeDriver(options);

        driver.manage().timeouts().implicitlyWait(java.time.Duration.ofMillis(timeoutMs));

        return driver;
    }
}