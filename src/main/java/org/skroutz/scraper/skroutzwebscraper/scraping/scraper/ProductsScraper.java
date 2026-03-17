package org.skroutz.scraper.skroutzwebscraper.scraping.scraper;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.skroutz.scraper.skroutzwebscraper.scraping.dto.ScrapedProductData;
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

    public ProductsScraper(ApplicationContext applicationContext,
                          @Value("${scraper.base-url}") String baseUrl) {
        super(applicationContext, baseUrl);
    }

    public List<ScrapedProductData> scrapeProducts(String url) {
        List<ScrapedProductData> result = executeWithWebDriver(webDriver -> {
            webDriver.get(url);
            WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(15));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("ol")));
            String renderedHtml = webDriver.getPageSource();
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

    private List<ScrapedProductData> scrapeProductsFromPage(String htmlPage) {
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

    private List<ScrapedProductData> extractProducts(Elements productElements) {
        List<ScrapedProductData> products = new ArrayList<>();
        int counter = 1;

        for (Element productElement : productElements) {
            try {
                log.info("Processing product element {}/{}", counter++, productElements.size());
                ScrapedProductData product = extractSingleProduct(productElement);
                products.add(product);
            } catch (Exception e) {
                log.error("Failed to parse product element: {}", e.getMessage());
            }
        }

        return products;
    }

    private ScrapedProductData extractSingleProduct(Element productElement) {
        return new ScrapedProductData(
                extractTitle(productElement),
                extractUrl(productElement),
                extractPrice(productElement),
                extractDescription(productElement),
                extractRating(productElement),
                extractImageUrl(productElement)
        );
    }

    Integer parsePaginationInfo(String htmlPage) {
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

    String extractUrl(Element productElement) {
        try {
            Element aTag = productElement.selectFirst(HtmlFields.PRODUCT_LINK);
            return aTag != null ? ensureAbsoluteUrl(aTag.attr("href")) : null;
        } catch (Exception e) {
            log.debug("Could not extract URL: {}", e.getMessage());
            return null;
        }
    }

    String extractTitle(Element productElement) {
        try {
            Element aTag = productElement.selectFirst(HtmlFields.PRODUCT_LINK);
            return aTag != null ? aTag.attr("title") : null;
        } catch (Exception e) {
            log.debug("Could not extract title: {}", e.getMessage());
            return null;
        }
    }

    BigDecimal extractPrice(Element productElement) {
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
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
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

    String extractDescription(Element productElement) {
        try {
            Element desc = productElement.selectFirst(HtmlFields.DESCRIPTION);
            return desc != null ? desc.text() : null;
        } catch (Exception e) {
            log.debug("Could not extract description: {}", e.getMessage());
            return null;
        }
    }

    BigDecimal extractRating(Element productElement) {
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

    String extractImageUrl(Element productElement) {
        if (productElement == null) {
            return null;
        }

        Element img = productElement.selectFirst(HtmlFields.IMAGE_CONTAINER);
        return img != null ? img.attr("src") : null;
    }
}
