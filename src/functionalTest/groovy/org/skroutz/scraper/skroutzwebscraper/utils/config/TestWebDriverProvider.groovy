package org.skroutz.scraper.skroutzwebscraper.utils.config

import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver

class TestWebDriverProvider {

    private String seleniumHost
    private int seleniumPort
    private String mockServerUrl

    // Constructor for Docker Compose setup
    TestWebDriverProvider() {
        // When running with Docker Compose, services are accessible via localhost
        this.seleniumHost = "localhost"
        this.seleniumPort = 4444
        this.mockServerUrl = "http://localhost:8080"  // Note: updated to port 8080
    }

    // Alternative constructor for custom configuration
    TestWebDriverProvider(String seleniumHost, int seleniumPort, String mockServerUrl) {
        this.seleniumHost = seleniumHost
        this.seleniumPort = seleniumPort
        this.mockServerUrl = mockServerUrl
    }

    WebDriver createWebDriver() {
        try {
            def options = new ChromeOptions()
            options.addArguments("--headless=new")  // Use new headless mode
            options.addArguments("--no-sandbox")
            options.addArguments("--disable-dev-shm-usage")
            options.addArguments("--disable-gpu")
            options.addArguments("--window-size=1920,1080")
            options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36");

            def seleniumUri = URI.create("http://${seleniumHost}:${seleniumPort}/wd/hub")
            return new RemoteWebDriver(seleniumUri.toURL(), options)
        } catch (Exception e) {
            throw new RuntimeException("Failed to create WebDriver", e)
        }
    }

    String getMockServerUrl() {
        return mockServerUrl
    }

    // Helper method to get the mock server URL for container-to-container communication
    // This would be used when the test itself runs inside a container
    static String getInternalMockServerUrl() {
        return "http://mockserver:80"
    }
}
