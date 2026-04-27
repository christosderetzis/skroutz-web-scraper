package org.skroutz.scraper.skroutzwebscraper.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

@Component
public class UrlBuilder {

    private final String baseUrl;

    public UrlBuilder(@Value("${scraper.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String convertToJsonUrl(String url) {
        if (url == null) {
            throw new IllegalArgumentException("URL cannot be null");
        }

        if (url.contains(".html")) {
            return url.replace(".html", ".json");
        }
        return url;
    }

    public String buildUrlWithPage(String baseUrl, int page) {
        if (page <= 0) {
            throw new IllegalArgumentException("Page number must be positive");
        }

        if (page == 1) {
            return baseUrl;
        }

        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "page=" + page;
    }

    public String buildFullProductUrl(String partialUrl) {
        if (partialUrl == null || partialUrl.trim().isEmpty()) {
            return null;
        }

        // If the URL is already complete, return it as-is
        if (partialUrl.startsWith("http://") || partialUrl.startsWith("https://")) {
            return partialUrl;
        }

        // Remove leading slash if present to avoid double slashes
        String cleanUrl = partialUrl.startsWith("/") ? partialUrl.substring(1) : partialUrl;

        return baseUrl + "/" + cleanUrl;
    }

    public String buildReviewsApiUrl(String productUrl, int offset) {
        if (productUrl == null || productUrl.isBlank()) {
            throw new IllegalArgumentException("Product URL cannot be null or blank");
        }

        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be non-negative");
        }

        try {
            URI uri = new URI(productUrl);
            String path = uri.getPath();

            if (path == null || path.isEmpty()) {
                throw new IllegalArgumentException("Product URL must have a path component");
            }

            String basePath = stripHtmlExtension(path);
            String newPath = basePath + "/reviews.json";
            String newQuery = "offset=" + offset;

            URI apiUri = new URI(uri.getScheme(), uri.getAuthority(), newPath, newQuery, null);
            return apiUri.toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid product URL: " + productUrl, e);
        }
    }

    public String buildPriceGraphApiUrl(String productUrl) {
        if (productUrl == null || productUrl.isBlank()) {
            throw new IllegalArgumentException("Product URL cannot be null or blank");
        }

        try {
            URI uri = new URI(productUrl);
            String path = uri.getPath();

            if (path == null || path.isEmpty()) {
                throw new IllegalArgumentException("Product URL must have a path component");
            }

            String basePath = stripHtmlExtension(path);
            String newPath = basePath + "/price_graph.json";
            String newQuery = "shipping_country=GR&currency=EUR";

            URI apiUri = new URI(uri.getScheme(), uri.getAuthority(), newPath, newQuery, null);
            return apiUri.toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid product URL: " + productUrl, e);
        }
    }

    private String stripHtmlExtension(String path) {
        if (path.endsWith(".html")) {
            return path.substring(0, path.length() - 5);
        }
        return path;
    }
}
