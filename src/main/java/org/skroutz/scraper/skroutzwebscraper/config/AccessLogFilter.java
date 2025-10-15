package org.skroutz.scraper.skroutzwebscraper.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.skroutz.scraper.skroutzwebscraper.utils.ServletUtils;
import org.slf4j.event.Level;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.LogRecord;

@Slf4j
@Component
@WebFilter("/*")
@Order
public class AccessLogFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, jakarta.servlet.ServletException {
        ContentCachingRequestWrapper httpRequest = new ContentCachingRequestWrapper((HttpServletRequest) request);
        CachedBodyHttpServletResponse httpResponse = new CachedBodyHttpServletResponse((HttpServletResponse) response);

        long startTime = System.currentTimeMillis();
        chain.doFilter(httpRequest, httpResponse);
        long duration = System.currentTimeMillis() - startTime;

        String path = httpRequest.getRequestURI();

        if (path.contains("actuator") || path.contains("api-doc") || path.contains("swagger")) {
            return;
        }

        String remoteHost = StringUtils.firstNonBlank(httpRequest.getRemoteHost(), httpRequest.getRemoteAddr());

        StringBuilder logMessage = new StringBuilder()
                .append(httpRequest.getMethod()).append(" ")
                .append(ServletUtils.getPath(httpRequest)).append(" ")
                .append(httpResponse.getStatus()).append(" ")
                .append(duration).append("ms ")
                .append(remoteHost);

        boolean requestFailed = httpResponse.getStatus() / 100 != 2;

        if (requestFailed && httpRequest.getContentAsByteArray().length > 0) {
            logMessage.append("\nREQUEST BODY:\n").append(new String(httpRequest.getContentAsByteArray(), StandardCharsets.UTF_8));
        }

        if (requestFailed && httpResponse.getBody().length > 0) {
            logMessage.append("\nRESPONSE BODY:\n").append(new String(httpResponse.getBody(), StandardCharsets.UTF_8));
        }

        log.makeLoggingEventBuilder(requestFailed ? Level.ERROR : Level.INFO).log(logMessage.toString());
    }
}
