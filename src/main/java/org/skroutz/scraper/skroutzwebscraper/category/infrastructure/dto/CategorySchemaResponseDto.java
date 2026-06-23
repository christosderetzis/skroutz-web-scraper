package org.skroutz.scraper.skroutzwebscraper.category.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.CategoryMappingSchema;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategorySchemaResponseDto {

    private Long id;
    private String category;
    private CategoryMappingSchema schema;
    private int version;
    private Timestamp createdAt;
}
