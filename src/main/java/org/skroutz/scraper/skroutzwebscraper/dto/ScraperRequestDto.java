package org.skroutz.scraper.skroutzwebscraper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ScraperRequestDto {

    @Pattern(regexp = "^https?://.+", message = "URL must be a valid HTTP or HTTPS URL")
    @NotBlank(message = "URL is required")
    private String url;

    @NotBlank(message = "Category is required")
    private String category;
}
