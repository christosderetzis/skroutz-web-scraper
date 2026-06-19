package org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceHistoryResponseApiDto {

    @JsonProperty("min_price")
    private MetricDataDto minPrice;

    @JsonProperty("popularity")
    private MetricDataDto popularity;

    @JsonProperty("shop_count")
    private MetricDataDto shopCount;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MetricDataDto {

        @JsonProperty("min")
        private BigDecimal min;

        @JsonProperty("max")
        private BigDecimal max;

        @JsonProperty("graphData")
        private GraphDataDto graphData;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GraphDataDto {

        @JsonProperty("1_months")
        private TimePeriodDto oneMonth;

        @JsonProperty("3_months")
        private TimePeriodDto threeMonths;

        @JsonProperty("6_months")
        private TimePeriodDto sixMonths;

        @JsonProperty("all")
        private TimePeriodDto all;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TimePeriodDto {

        @JsonProperty("values")
        private List<DataPointDto> values;

        @JsonProperty("has_values")
        private Boolean hasValues;

        @JsonProperty("label")
        private String label;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DataPointDto {

        @JsonProperty("shop_name")
        private String shopName;

        @JsonProperty("timestamp")
        private Long timestamp;

        @JsonProperty("value")
        private BigDecimal value;
    }
}
