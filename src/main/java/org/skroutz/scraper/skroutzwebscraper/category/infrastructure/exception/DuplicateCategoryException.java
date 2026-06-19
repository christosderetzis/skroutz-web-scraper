package org.skroutz.scraper.skroutzwebscraper.category.infrastructure.exception;

public class DuplicateCategoryException extends RuntimeException {

    public DuplicateCategoryException(String category) {
        super(String.format("Category schema already exists for category: %s", category));
    }
}
