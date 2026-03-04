package org.skroutz.scraper.skroutzwebscraper.scraper;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class ProductsScraper extends AbstractScraper {

    private final String baseUrl;

    public ProductsScraper(ApplicationContext applicationContext,
                          @Value("${scraper.base-url}") String baseUrl) {
        super(applicationContext);
        this.baseUrl = baseUrl;
    }

    public List<Product> scrapeProducts(String url) {
        List<Product> result = executeWithWebDriver(webDriver -> {
            // 1. Open page
            webDriver.get(url);

            // 2. Wait until page content is rendered
            WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(15));
            wait.until(
                    ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector("ol") // listing container
                    )
            );

            // 3. Get rendered HTML (after JS execution)
            String renderedHtml = webDriver.getPageSource();

            // 4. Scrape products from the rendered HTML
            return scrapeProductsFromPage(renderedHtml);
        }, url, "scraping products");
        return result != null ? result : List.of();
    }

    public Integer getNumberOfPages(String url) {
        Integer result = executeWithWebDriver(webDriver -> {
            webDriver.get(url);

            String renderedHtml = webDriver.getPageSource();
            return parsePaginationInfo(renderedHtml);
        }, url, "getting number of pages");
        return result != null ? result : 0;
    }

    private List<Product> scrapeProductsFromPage(String htmlPage) {
        Document doc = Jsoup.parse(htmlPage);
        Element olElement = doc.selectFirst(HtmlFields.LISTING_CONTAINER);

        if (olElement == null) {
            log.warn("Listing container not found in the HTML page. Page structure might have changed.");
            return List.of();
        }

        Elements productElements = olElement.selectXpath(HtmlFields.PRODUCT_ITEM_XPATH);

        if (productElements.isEmpty()) {
            log.warn("No product elements found in the listing container. Page might be empty or structure changed.");
            return List.of();
        }

        log.info("Successfully found {} product elements in the HTML page", productElements.size());

        return extractProducts(productElements);
    }

    private List<Product> extractProducts(Elements productElements) {
        List<Product> products = new ArrayList<>();
        int counter = 1;

        for (Element productElement : productElements) {
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

    private Product extractSingleProduct(Element productElement) {
       Product product = new Product();
       product.setTitle(extractTitle(productElement));
       product.setUrl(extractUrl(productElement));
       product.setPrice(extractPrice(productElement));
       product.setDescription(extractDescription(productElement));
       product.setRating(extractRating(productElement));
       product.setImageUrl(extractImageUrl(productElement));
       return product;
    }

    private Integer parsePaginationInfo(String htmlPage) {
        try {
            Document document = Jsoup.parse(htmlPage);
            Element paginationSpan = document.selectFirst(HtmlFields.PAGINATION_BUTTON);

            if (paginationSpan == null) {
                return null;
            }

            String paginationText = paginationSpan.text();

            if (paginationText.split(" ").length == 3) {
                List<String> parts = Arrays.stream(paginationText.split(" ")).toList();
                return Integer.parseInt(parts.get(2));
            }

            log.warn("Could not parse pagination text: {}", paginationText);
            return null;

        } catch (Exception e) {
            log.warn("Could not extract pagination info: {}", e.getMessage());
            return null;
        }
    }

    private String extractUrl(Element productElement) {
        try {
            Element aTag = productElement.selectFirst(HtmlFields.PRODUCT_LINK);
            return aTag != null ? aTag.attr("href") : null;
        } catch (Exception e) {
            log.debug("Could not extract URL: {}", e.getMessage());
            return null;
        }
    }

    private String extractTitle(Element productElement) {
        try {
            Element aTag = productElement.selectFirst(HtmlFields.PRODUCT_LINK);
            return aTag != null ? ensureAbsoluteUrl(aTag.attr("title")) : null;
        } catch (Exception e) {
            log.debug("Could not extract title: {}", e.getMessage());
            return null;
        }
    }

    private BigDecimal extractPrice(Element productElement) {
        try {
            Element priceSpan = productElement.selectFirst(HtmlFields.PRICE_LINK);

            if (priceSpan != null) {
                String priceText = processPrice(priceSpan.text());
                return new BigDecimal(priceText);
            }

        } catch (NumberFormatException e) {
            log.debug("Could not extract price: {}", e.getMessage());
        }
        return null;
    }

    private String ensureAbsoluteUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        // If URL is already absolute (starts with http:// or https://), return as-is
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        // If URL is relative, prepend base URL
        return baseUrl + url;
    }

    private String processPrice(String priceText) {
        priceText = priceText
                .replace("from", "")
                .replace("€", "")
                .replace("από", "")
                .trim();

        if (priceText.contains("-")) {
            priceText = priceText.split("-")[0].trim();
        }

        return priceText.replace(".", "").replace(",", ".");
    }

    private String extractDescription(Element productElement) {
        try {
            Element desc = productElement.selectFirst(HtmlFields.DESCRIPTION);
            return desc != null ? desc.text() : null;
        } catch (Exception e) {
            log.debug("Could not extract description: {}", e.getMessage());
            return null;
        }
    }

    private BigDecimal extractRating(Element productElement) {
        try {
            Element ratingSpan = productElement.selectFirst(HtmlFields.RATING);

            if (ratingSpan != null) {
                String ratingText = ratingSpan.text().replace(",", ".");
                return new BigDecimal(ratingText);
            }

        } catch (Exception e) {
            log.debug("Could not extract rating: {}", e.getMessage());
        }
        return null;
    }

    private String extractImageUrl(Element productElement) {
        if (productElement == null) {
            return null;
        }

        Element img = productElement.selectFirst(HtmlFields.IMAGE_CONTAINER);
        return img != null ? img.attr("src") : null;
    }
}
