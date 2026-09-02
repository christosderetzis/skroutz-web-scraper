package org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.apache.commons.lang3.StringUtils;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.ProductSearchRequest;

public class SearchQueryRequiredValidator implements ConstraintValidator<SearchQueryRequired, ProductSearchRequest> {

    @Override
    public boolean isValid(ProductSearchRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        return StringUtils.isNotBlank(request.getCategory()) || StringUtils.isNotBlank(request.getSearchTerm());
    }
}
