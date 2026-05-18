package org.skroutz.scraper.skroutzwebscraper.dto.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SpecFacetBucketDto {

    private String value;
    private long count;
}
