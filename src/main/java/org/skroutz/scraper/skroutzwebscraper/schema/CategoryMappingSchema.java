package org.skroutz.scraper.skroutzwebscraper.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class
CategoryMappingSchema {

    @Builder.Default
    private List<DirectFieldMapping> directFields = new ArrayList<>();

    @Builder.Default
    private List<FeatureFieldMapping> arrayFields = new ArrayList<>();
}
