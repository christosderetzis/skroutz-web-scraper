package org.skroutz.scraper.skroutzwebscraper.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
}
