package org.skroutz.scraper.skroutzwebscraper.category.infrastructure.exception;

public class CategorySchemaNotFoundException extends RuntimeException {

    public CategorySchemaNotFoundException(String category) {
        super(String.format("Category schema not found for category: %s", category));
    }
}
