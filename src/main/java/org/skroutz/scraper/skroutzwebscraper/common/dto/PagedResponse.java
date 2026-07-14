package org.skroutz.scraper.skroutzwebscraper.common.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record PagedResponse<T>(
        List<T> content,
        PageMetadata metadata
) {
    public record PageMetadata(
            int pageNumber,
            int pageSize,
            long totalElements,
            int totalPages,
            boolean isFirst,
            boolean isLast
    ) {}

    // Factory method to cleanly convert Spring's Page into your custom DTO structure
    public static <T> PagedResponse<T> from(Page<T> springPage) {
        PageMetadata metadata = new PageMetadata(
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages(),
                springPage.isFirst(),
                springPage.isLast()
        );
        return new PagedResponse<>(springPage.getContent(), metadata);
    }
}
