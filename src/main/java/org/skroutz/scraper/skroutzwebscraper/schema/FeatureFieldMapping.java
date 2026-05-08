package org.skroutz.scraper.skroutzwebscraper.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFieldMapping {

    private String path;
    private String target;

    @Builder.Default
    private FeatureExtraction type = FeatureExtraction.VALUE;
}
