
package org.skroutz.scraper.skroutzwebscraper.category.domain.schema;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum FeatureExtraction {
    VALUE, YES_KEY, COMMA_SPLIT, YES_GROUP;

    @JsonCreator
    public static FeatureExtraction fromValue(String value) {
        if ("yes_key".equalsIgnoreCase(value))    return YES_KEY;
        if ("comma_split".equalsIgnoreCase(value)) return COMMA_SPLIT;
        if ("yes_group".equalsIgnoreCase(value)) return YES_GROUP;
        return VALUE;
    }
}
