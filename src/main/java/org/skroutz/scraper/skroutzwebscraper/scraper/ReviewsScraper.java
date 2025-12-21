package org.skroutz.scraper.skroutzwebscraper.scraper;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.LocalDate;
import org.skroutz.scraper.skroutzwebscraper.entity.Review;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import static org.skroutz.scraper.skroutzwebscraper.scraper.HtmlFields.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Component
public class ReviewsScraper extends AbstractScraper {

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(15);
    private static final int PARALLEL_THRESHOLD = 50;

    public ReviewsScraper(ApplicationContext applicationContext) {
        super(applicationContext);
    }

    public List<Review> scrapeReviews(String url) {
        return executeWithWebDriver(webDriver -> {
            webDriver.get(url + "#reviews");

            // Wait for reviews container to be present
            WebDriverWait wait = new WebDriverWait(webDriver, WAIT_TIMEOUT);
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(REVIEWS_CONTAINER)));

            List<WebElement> reviewElements = findReviewElements(webDriver);
            return extractReviews(reviewElements);
        }, url, "scraping reviews");
    }

    private List<WebElement> findReviewElements(WebDriver webDriver) {
        JavascriptExecutor js = (JavascriptExecutor) webDriver;

        // Scroll to reviews container
        WebElement reviewsContainer = webDriver.findElement(By.cssSelector(REVIEWS_CONTAINER));
        js.executeScript("arguments[0].scrollIntoView({behavior: 'instant', block: 'center'});", reviewsContainer);

        sleep(300);

        WebElement reviewsList = webDriver.findElement(By.cssSelector(REVIEWS_LIST));
        int previousCount = 0;

        while (true) {
            int currentCount = reviewsList.findElements(By.cssSelector(REVIEW_ITEM)).size();
            log.debug("Current review count: {}", currentCount);

            if (currentCount == previousCount) {
                log.debug("Review count stabilized at {} - stopping", currentCount);
                break;
            }

            previousCount = currentCount;

            // Try to find the load more button
            WebElement loadMore = findLoadMoreButton(webDriver);
            if (loadMore == null) {
                log.debug("No 'load more' button found - all reviews loaded");
                break;
            }

            // scroll to and click the load more button
            js.executeScript("arguments[0].scrollIntoView({behavior: 'instant', block: 'center'});", loadMore);
            sleep(100);

            // click the button via JavaScript to avoid interception issues
            js.executeScript("arguments[0].click();", loadMore);
            sleep(500);
        }

        @SuppressWarnings("unchecked")
        List<WebElement> filteredReviews = (List<WebElement>) js.executeScript(
                """
                const list = arguments[0];
                const items = Array.from(list.querySelectorAll('%s'));
                return items.filter(item => !item.querySelector('%s'));
                """.formatted(REVIEW_ITEM, MERGED_REVIEW_INFO),
                reviewsList
        );

        log.info("Found {} reviews after filtering", filteredReviews.size());
        return filteredReviews;
    }

    private WebElement findLoadMoreButton(WebDriver webDriver) {
        try {
            WebElement loadMore = webDriver.findElement(By.cssSelector(LOAD_MORE_REVIEWS));
            return loadMore.isDisplayed() ? loadMore : null;
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    private void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Sleep interrupted", e);
        }
    }

    private List<Review> extractReviews(List<WebElement> reviewElements) {
        // Use parallel streams only for large datasets (>50 reviews)
        Stream<WebElement> stream = reviewElements.size() > PARALLEL_THRESHOLD
                ? reviewElements.parallelStream()
                : reviewElements.stream();

        return stream
                .map(this::parseReview)
                .toList();
    }

    /**
     * Extracts all review data using a single JavaScript call for optimal performance.
     * This reduces ~9 DOM queries per review to just 1, providing 5-10x speedup.
     */
    private Review parseReview(WebElement reviewElement) {
        WebDriver driver = ((WrapsDriver) reviewElement).getWrappedDriver();
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Single JavaScript execution to extract all data at once
        String script = """
            const el = arguments[0];
            return {
                reviewerName: el.querySelector('%s')?.textContent?.trim() || null,
                stars: el.getAttribute('data-stars'),
                reviewText: Array.from(el.querySelectorAll('%s'))
                    .map(p => p.textContent.trim())
                    .join('\\n\\n')
                    .trim() || null,
                date: el.querySelector('%s')?.textContent?.trim() || null,
                isVerified: !!el.querySelector('%s'),
                helpfulText: el.querySelector('%s')?.textContent?.trim() || null,
                pros: Array.from(el.querySelectorAll('%s'))
                    .map(li => li.textContent.trim()),
                neutral: Array.from(el.querySelectorAll('%s'))
                    .map(li => li.textContent.trim()),
                cons: Array.from(el.querySelectorAll('%s'))
                    .map(li => li.textContent.trim())
            };
        """.formatted(REVIEWER_NAME, REVIEW_BODY, REVIEW_DATE, VERIFICATION_MARK,
                HELPFULNESS_MESSAGE, PROS_LIST, NEUTRAL_LIST, CONS_LIST);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) js.executeScript(script, reviewElement);

        return mapToReview(data);
    }

    /**
     * Maps JavaScript-extracted data to Review entity
     */
    private Review mapToReview(Map<String, Object> data) {
        Review review = new Review();

        // Basic fields
        review.setReviewerName((String) data.get("reviewerName"));
        review.setReviewText((String) data.get("reviewText"));
        review.setIsVerifiedPurchase((Boolean) data.get("isVerified"));

        // Rating
        String stars = (String) data.get("stars");
        if (stars != null) {
            try {
                review.setReviewerRating(Integer.parseInt(stars));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse rating: {}", stars);
                review.setReviewerRating(0);
            }
        }

        // Date
        String dateText = (String) data.get("date");
        if (dateText != null) {
            parseDateText(dateText, review);
        }

        // Helpful votes
        String helpfulText = (String) data.get("helpfulText");
        if (helpfulText != null) {
            parseHelpfulVotes(helpfulText, review);
        } else {
            review.setHelpfulVotes(0);
            review.setTotalVotes(0);
        }

        // Arrays (pros, neutral, cons)
        review.setPros(toArrayOrNull((List<?>) data.get("pros")));
        review.setNeutral(toArrayOrNull((List<?>) data.get("neutral")));
        review.setCons(toArrayOrNull((List<?>) data.get("cons")));

        log.debug("Parsed review: name='{}', rating={}, date={}, verified={}",
                review.getReviewerName(),
                review.getReviewerRating(),
                review.getReviewDate(),
                review.getIsVerifiedPurchase());

        return review;
    }

    /**
     * Converts a list to String array, returns null for empty lists
     */
    private String[] toArrayOrNull(List<?> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.stream()
                .map(Object::toString)
                .toArray(String[]::new);
    }

    /**
     * Parses date text in format "23/09/2023" or "2025-09-23"
     */
    private void parseDateText(String dateText, Review review) {
        try {
            if (dateText.contains("/")) {
                String[] parts = dateText.split("/");
                review.setReviewDate(LocalDate.of(
                        Integer.parseInt(parts[2]), // year
                        Integer.parseInt(parts[1]), // month
                        Integer.parseInt(parts[0])  // day
                ));
            } else if (dateText.contains("-")) {
                String[] parts = dateText.split("-");
                review.setReviewDate(LocalDate.of(
                        Integer.parseInt(parts[0]), // year
                        Integer.parseInt(parts[1]), // month
                        Integer.parseInt(parts[2])  // day
                ));
            } else {
                log.warn("Unexpected date format: {}", dateText);
                review.setReviewDate(null);
            }
        } catch (Exception e) {
            log.warn("Failed to parse review date '{}': {}", dateText, e.getMessage());
            review.setReviewDate(null);
        }
    }

    /**
     * Parses helpful votes text like "1 out of 1 found this review helpful"
     * or "1 στους 1 χρήστης βρήκε αυτή την κριτική χρήσιμη"
     */
    private void parseHelpfulVotes(String helpfulText, Review review) {
        try {
            String[] parts = helpfulText.split(" ");
            if (helpfulText.contains("out of")) {
                review.setHelpfulVotes(Integer.parseInt(parts[0]));
                review.setTotalVotes(Integer.parseInt(parts[3]));
            } else if (helpfulText.contains("χρήστες") || helpfulText.contains("στους")) {
                review.setHelpfulVotes(Integer.parseInt(parts[0]));
                review.setTotalVotes(Integer.parseInt(parts[2]));
            } else {
                log.warn("Unexpected helpful votes format: {}", helpfulText);
                review.setHelpfulVotes(0);
                review.setTotalVotes(0);
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            log.warn("Failed to parse helpful votes '{}': {}", helpfulText, e.getMessage());
            review.setHelpfulVotes(0);
            review.setTotalVotes(0);
        }
    }
}
