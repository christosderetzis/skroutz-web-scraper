package org.skroutz.scraper.skroutzwebscraper.review.infrastructure.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.dto.ReviewSummaryDto;
import org.skroutz.scraper.skroutzwebscraper.review.domain.entity.ReviewSummary;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReviewSummaryMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "productId", source = "productId")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "pros", source = "dto.pros", qualifiedByName = "listToArray")
    @Mapping(target = "cons", source = "dto.cons", qualifiedByName = "listToArray")
    ReviewSummary toEntity(ReviewSummaryDto dto, Long productId);

    @Mapping(target = "pros", source = "pros", qualifiedByName = "arrayToList")
    @Mapping(target = "cons", source = "cons", qualifiedByName = "arrayToList")
    ReviewSummaryDto toDto(ReviewSummary entity);

    @Named("listToArray")
    default String[] listToArray(List<String> list) {
        return list != null ? list.toArray(new String[0]) : new String[0];
    }

    @Named("arrayToList")
    default List<String> arrayToList(String[] list) {
        return list != null ? List.of(list) : List.of();
    }
}
