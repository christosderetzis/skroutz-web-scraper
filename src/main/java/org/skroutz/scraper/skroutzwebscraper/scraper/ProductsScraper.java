package org.skroutz.scraper.skroutzwebscraper.scraper;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class ProductsScraper {

    private final ApplicationContext applicationContext;

    public ProductsScraper(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public List<Product> scrapeProducts(String url) {
        WebDriver webDriver = null;
        try {
            // Get a fresh WebDriver instance for each scraping operation
            webDriver = applicationContext.getBean(WebDriver.class);
            webDriver.get(url);
            List<WebElement> productElements = findProductElements(webDriver);
            return extractProducts(productElements);
        } catch (Exception e) {
            log.error("Error scraping products from {}: {}", url, e.getMessage(), e);
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
        return List.of();
    }

    public Integer getNumberOfPages(String url) {
        WebDriver webDriver = null;
        try {
            // Get a fresh WebDriver instance for each scraping operation
            webDriver = applicationContext.getBean(WebDriver.class);
            webDriver.get(url);

            // Find the pagination span that contains "1 from 11" text
            WebElement paginationSpan = webDriver.findElement(By.cssSelector(".paginator button span"));
            String paginationText = paginationSpan.getText();

            // Extract the total number of pages from text like "1 from 11"
            if (paginationText.contains("from")) {
                List<String> parts = Arrays.stream(paginationText.split(" ")).toList();
                if (parts.size() == 3) {
                    String totalPagesText = parts.get(2);
                    return Integer.parseInt(totalPagesText);
                }
            }
            
            log.warn("Could not parse pagination text: {}", paginationText);
            return null;
        } catch (Exception e) {
            log.error("Error getting number of pages from {}: {}", url, e.getMessage(), e);
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
        return 0;
    }

    private List<WebElement> findProductElements(WebDriver webDriver) {
        try {
            // Wait for page to fully load and for the listing container to be present
            WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(20));
            
            // Try different possible selectors for the listing container
            WebElement olElement = null;
            
            try {
                olElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("ol.listing-list")));
                log.info("Found ol.listing-list element");
            } catch (Exception e) {
                log.warn("ol.listing-list not found, trying fallback selector 'ol'");
                try {
                    olElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("ol")));
                    log.info("Found ol element");
                } catch (Exception e2) {
                    log.warn("ol not found, trying fallback selector '.listing-list'");
                    olElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".listing-list")));
                    log.info("Found .listing-list element");
                }
            }
            
            if (olElement == null) {
                throw new RuntimeException("Could not find any listing container element");
            }
            
            List<WebElement> filteredItems = olElement.findElements(By.xpath(ProductWebCssFields.PRODUCT_ITEM_XPATH));
            log.info("Found {} product elements", filteredItems.size());
            
            if (filteredItems.isEmpty()) {
                log.warn("No products found on this page. Page might be empty or structure changed.");
                // Log page source for debugging
                log.debug("Page title: {}", webDriver.getTitle());
                log.debug("Current URL: {}", webDriver.getCurrentUrl());
            }
            
            return filteredItems;
            
        } catch (Exception e) {
            log.error("Error finding product elements: {}", e.getMessage());
            log.debug("Page title: {}", webDriver.getTitle());
            log.debug("Current URL: {}", webDriver.getCurrentUrl());
            return new ArrayList<>();
        }
    }

    private List<Product> extractProducts(List<WebElement> productElements) {
        List<Product> products = new ArrayList<>();
        int counter = 1;
        for (WebElement productElement : productElements) {
            try {
                log.info("Processing product element {}/{}", counter++, productElements.size());
                Product product = extractSingleProduct(productElement);
                products.add(product);
            } catch (Exception e) {
                log.error("Failed to parse product element: {}", e.getMessage());
            }
        }
        
        return products;
    }

    private Product extractSingleProduct(WebElement productElement) {
        Product product = new Product();
        
        extractUrlAndTitle(productElement, product);
        extractPrice(productElement, product);
        extractImageUrl(productElement, product);
        extractDescription(productElement, product);
        extractRating(productElement, product);
        
        return product;
    }

    private void extractUrlAndTitle(WebElement productElement, Product product) {
        try {
            log.info(" -------------------- Extracting URL and title -------------------- ");
            WebElement aTag = productElement.findElement(By.cssSelector(ProductWebCssFields.PRODUCT_LINK));
            product.setUrl(aTag.getAttribute("href"));
            product.setTitle(aTag.getAttribute("title"));
        } catch (NoSuchElementException e) {
            log.debug("Could not extract URL and title: {}", e.getMessage());
        }
    }

    private void extractPrice(WebElement productElement, Product product) {
        try {
            log.info(" -------------------- Extracting price -------------------- ");
            WebElement priceSpan = productElement.findElement(By.cssSelector(ProductWebCssFields.PRICE_LINK));
            String priceText = priceSpan.getText()
                    .replace("από", "")
                    .replace("€", "")
                    .trim();
            
            // Handle price ranges like "500,00 - 600,00" - take only the first value
            if (priceText.contains("-")) {
                priceText = priceText.split("-")[0].trim();
            }
            
            // Clean up the price text for parsing
            priceText = priceText.replace(".", "").replace(",", ".");
            
            product.setPrice(new BigDecimal(priceText));
        } catch (NoSuchElementException | NumberFormatException e) {
            log.debug("Could not extract price: {}", e.getMessage());
            product.setPrice(null);
        }
    }

    private void extractImageUrl(WebElement productElement, Product product) {
        try {
            log.info(" -------------------- Extracting image URL -------------------- ");
            WebElement img = productElement.findElement(By.cssSelector(ProductWebCssFields.IMAGE_CONTAINER));
            product.setImageUrl(img.getAttribute("src"));
        } catch (NoSuchElementException e) {
            log.debug("Could not extract image URL: {}", e.getMessage());
            product.setImageUrl(null);
        }
    }

    private void extractDescription(WebElement productElement, Product product) {
        try {
            log.info(" -------------------- Extracting description -------------------- ");
            WebElement desc = productElement.findElement(By.cssSelector(ProductWebCssFields.DESCRIPTION));
            if (desc.isDisplayed()) {
                product.setDescription(desc.getText());
            } else {
                product.setDescription(null);
            }
        } catch (NoSuchElementException e) {
            log.debug("Could not extract description: {}", e.getMessage());
            product.setDescription(null);
        }
    }

    private void extractRating(WebElement productElement, Product product) {
        try {
            log.info(" -------------------- Extracting rating -------------------- ");
            WebElement ratingSpan = productElement.findElement(By.cssSelector(ProductWebCssFields.RATING));
            String ratingText = ratingSpan.getText().replace(",", ".");
            product.setRating(new BigDecimal(ratingText));
        } catch (NoSuchElementException | NumberFormatException e) {
            log.debug("Could not extract rating: {}", e.getMessage());
            product.setRating(null);
        }
    }
}
