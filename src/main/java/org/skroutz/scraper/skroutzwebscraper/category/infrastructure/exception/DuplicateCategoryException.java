package org.skroutz.scraper.skroutzwebscraper.category.infrastructure.exception;

import org.skroutz.scraper.skroutzwebscraper.common.exception.ConflictException;

public class DuplicateCategoryException extends ConflictException {

    public DuplicateCategoryException(String category) {
        super(String.format("Category schema already exists for category: %s", category));
    }
}
