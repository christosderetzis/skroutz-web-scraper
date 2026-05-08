package org.skroutz.scraper.skroutzwebscraper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.skroutz.scraper.skroutzwebscraper.schema.CategoryMappingSchema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategorySchemaCreateRequestDto {

    @NotBlank(message = "Category must not be blank")
    private String category;

    @NotNull(message = "Schema must not be null")
    private CategoryMappingSchema schema;
}
