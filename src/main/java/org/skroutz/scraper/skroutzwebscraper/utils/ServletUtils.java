package org.skroutz.scraper.skroutzwebscraper.utils;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class ServletUtils {

    private ServletUtils() {
    }

    public static String getPath(HttpServletRequest request) {
        String queryParams = getQueryParams(request);
        String path = request.getRequestURI();
        if (isNotBlank(queryParams)) {
            path = path + "?" + queryParams;
        }
        return path;
    }

    private static String getQueryParams(HttpServletRequest request) {
        return request.getParameterMap().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(stringEntry -> Arrays.stream(stringEntry.getValue())
                        .map(value -> stringEntry.getKey() + "=" + value)
                        .collect(Collectors.joining("&")))
                .collect(Collectors.joining("&"));
    }
}
