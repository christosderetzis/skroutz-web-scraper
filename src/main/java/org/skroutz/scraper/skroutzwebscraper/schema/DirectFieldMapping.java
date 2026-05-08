package org.skroutz.scraper.skroutzwebscraper.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectFieldMapping {

    private String path;
    private String target;

    @Builder.Default
    private FieldType type = FieldType.STRING;
}
