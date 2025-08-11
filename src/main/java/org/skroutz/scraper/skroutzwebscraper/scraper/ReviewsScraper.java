package org.skroutz.scraper.skroutzwebscraper.scraper;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.LocalDate;
import java.util.Random;
import org.skroutz.scraper.skroutzwebscraper.entity.Review;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class ReviewsScraper extends AbstractScraper {

    public ReviewsScraper(ApplicationContext applicationContext) {
        super(applicationContext);
    }

    public List<Review> scrapeReviews(String url) {
        return executeWithWebDriver(webDriver -> {
            webDriver.get(url + "#reviews");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            List<WebElement> reviewElements = findReviewElements(webDriver);
            return extractReviews(reviewElements);
        }, url, "scraping reviews");
    }

    private List<WebElement> findReviewElements(WebDriver webDriver) {
        WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(2));
        JavascriptExecutor js = (JavascriptExecutor) webDriver;
        // Scroll down to the reviews section
        js.executeScript("document.querySelector('#reviews-container').scrollIntoView();");

        int previousCount = webDriver.findElements(By.cssSelector("#sku_reviews_list .review-item")).size();

        while (true) {
            try {

                WebElement loadMore = wait.until(ExpectedConditions.elementToBeClickable(
                        By.cssSelector(".load-more-reviews")));

                // Scroll into view
                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", loadMore);

                // Click using JS to avoid "intercepted click"
                js.executeScript("arguments[0].click();", loadMore);

                // Wait until review count increases
                int finalPreviousCount = previousCount;
                wait.until(d -> {
                    int newCount = d.findElements(By.cssSelector("#sku_reviews_list .review-item")).size();
                    return newCount > finalPreviousCount;
                });

                // Update count
                previousCount = webDriver.findElements(By.cssSelector("#sku_reviews_list .review-item")).size();

            } catch (TimeoutException e) {
                // No more reviews to load or button gone
                break;
            }
        }

        // Grab all review items
        List<WebElement> reviewItems = webDriver.findElements(By.cssSelector("#sku_reviews_list .review-item"));

        List<WebElement> filteredReviews = reviewItems.stream()
                .filter(item -> item.findElements(By.cssSelector(".merged-review-info")).isEmpty())
                .toList();

        log.info("Found {} reviews after filtering", filteredReviews.size());
        return filteredReviews;
    }

    private List<Review> extractReviews(List<WebElement> reviewElements) {
        // Implement logic to extract review data from the WebElement list
        // This will depend on the specific structure of each review element
        return reviewElements.stream()
                .map(this::parseReview)
                .toList();
    }

    private Review parseReview(WebElement reviewElement) {
        // Create and return a Review object
        Review review = new Review();

        extractReviewText(reviewElement, review);
        extractReviewerName(reviewElement, review);
        extractReviewRating(reviewElement, review);
        extractHelpfulVotes(reviewElement, review);
        extractIsVerifiedPurchase(reviewElement, review);
        extractReviewDate(reviewElement, review);
        extractPros(reviewElement, review);
        extractNeutral(reviewElement, review);
        extractCons(reviewElement, review);

        return review;
    }

    private void extractReviewerName(WebElement reviewElement, Review review) {
        WebElement reviewerName = reviewElement.findElement(By.cssSelector("a.author"));
        review.setReviewerName(reviewerName.getText());
    }

    private void extractCons(WebElement reviewElement, Review review) {
        List<WebElement> consElements = reviewElement.findElements(By.cssSelector("ul.icon.cons > li"));
        String[] cons = consElements.stream()
                .map(WebElement::getText)
                .toArray(String[]::new);
        if (cons.length == 0) {
            log.warn("No cons found for this review, setting cons to null");
            cons = null;
        }
        review.setCons(cons);
    }

    private void extractNeutral(WebElement reviewElement, Review review) {
        List<WebElement> neutralElements = reviewElement.findElements(By.cssSelector("ul.icon.so-so > li"));
        String[] neutrals = neutralElements.stream()
                .map(WebElement::getText)
                .toArray(String[]::new);
        if (neutrals.length == 0) {
            log.warn("No neutrals found for this review, setting neutrals to null");
            neutrals = null;
        }
        review.setNeutral(neutrals);
    }

    private void extractPros(WebElement reviewElement, Review review) {
        List<WebElement> prosElements = reviewElement.findElements(By.cssSelector("ul.icon.pros > li"));
        String[] pros = prosElements.stream()
                .map(WebElement::getText)
                .toArray(String[]::new);
        if (pros.length == 0) {
            log.warn("No pros found for this review, setting pros to null");
            pros = null;
        }
        review.setPros(pros);
    }

    private void extractReviewDate(WebElement reviewElement, Review review) {
        WebElement reviewDate = reviewElement.findElement(By.cssSelector(".permalink.js-review-permalink"));
        String dateText = reviewDate.getText();
        // date format is like "23/09/2023" or "2025-09-23"
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
            log.warn("Failed to parse review date: {}", e.getMessage());
            review.setReviewDate(null);
        }

    }

    private void extractIsVerifiedPurchase(WebElement reviewElement, Review review) {
        try {
            reviewElement.findElement(By.cssSelector(".verification-mark"));
            review.setIsVerifiedPurchase(true);
        } catch (NoSuchElementException e) {
            log.warn("No verification mark found for this review, setting isVerifiedPurchase to false");
            review.setIsVerifiedPurchase(false);
        }
    }

    private void extractHelpfulVotes(WebElement reviewElement, Review review) {
        try {
            WebElement helpfulVotesElement = reviewElement.findElement(By.cssSelector(".helpfulness-message"));
            // message is like 1 out of 1 found this review helpful or 1 στους 1 χρήστης βρήκε αυτή την κριτική χρήσιμη
            String helpfulText = helpfulVotesElement.getText();
            String[] parts = helpfulText.split(" ");
            if (helpfulText.contains("out of")) {
                // Assuming the format is "1 out of 1" or "1 στους 1 χρήστης"
                review.setHelpfulVotes(Integer.parseInt(parts[0]));
                review.setTotalVotes(Integer.parseInt(parts[3]));
            } else if (helpfulText.contains("χρήστης")) {
                // Assuming the format is "1 στους 1 χρήστης βρήκε αυτή την κριτική χρήσιμη"
                review.setHelpfulVotes(Integer.parseInt(parts[0]));
                review.setTotalVotes(Integer.parseInt(parts[2]));
            } else {
                log.warn("Unexpected helpful votes format: {}", helpfulText);
                review.setHelpfulVotes(0);
                review.setTotalVotes(0);
            }
        } catch (NoSuchElementException e) {
            log.warn("No helpful votes found for this review, setting helpfulVotes and totalVotes to 0");
            review.setHelpfulVotes(0);
            review.setTotalVotes(0);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse helpful votes: {}", e.getMessage());
            review.setHelpfulVotes(0);
            review.setTotalVotes(0);
        }
    }

    private void extractReviewText(WebElement reviewElement, Review review) {
        List<WebElement> paragraphs = reviewElement.findElements(By.cssSelector(".review-body p"));

        String cleanReviewText = null;
        if (!paragraphs.isEmpty() ) {
            StringBuilder sb = new StringBuilder();
            for (WebElement p : paragraphs) {
                sb.append(p.getText()).append("\n\n");
            }
            cleanReviewText = sb.toString().trim();
        }

        review.setReviewText(cleanReviewText);
    }

    private void extractReviewRating(WebElement reviewElement, Review review) {
        String stars = reviewElement.getDomAttribute("data-stars");
        review.setReviewerRating(Integer.parseInt(stars));
    }
}
