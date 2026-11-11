package org.skroutz.scraper.skroutzwebscraper.category.infrastructure.exception;

import org.skroutz.scraper.skroutzwebscraper.common.exception.NotFoundException;

public class CategorySchemaNotFoundException extends NotFoundException {
    public CategorySchemaNotFoundException(String category) {
        super("Category schema not found for category: " + category);
    }
}
