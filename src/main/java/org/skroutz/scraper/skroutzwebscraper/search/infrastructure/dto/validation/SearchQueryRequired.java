package org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Constraint(validatedBy = SearchQueryRequiredValidator.class)
public @interface SearchQueryRequired {

    String message() default "Either category or searchTerm must be provided";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
