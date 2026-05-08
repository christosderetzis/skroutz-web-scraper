package org.skroutz.scraper.skroutzwebscraper.schema;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum FieldType {
    STRING, INTEGER, NUMERIC;

    @JsonCreator
    public static FieldType fromValue(String value) {
        if (value == null) return STRING;
        return switch (value.toLowerCase()) {
            case "integer" -> INTEGER;
            case "numeric" -> NUMERIC;
            default -> STRING;
        };
    }
}
