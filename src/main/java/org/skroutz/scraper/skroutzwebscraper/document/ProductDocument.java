package org.skroutz.scraper.skroutzwebscraper.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Document(indexName = "products")
@Setting(settingPath = "elasticsearch/product-settings.json")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Keyword)
    private String url;

    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "english_analyzer"),
        otherFields = {
            @InnerField(suffix = "autocomplete", type = FieldType.Text, analyzer = "autocomplete")
        }
    )
    private String title;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Keyword, index = false)
    private String imageUrl;

    @Field(type = FieldType.Text, analyzer = "english_analyzer")
    private String description;

    @Field(type = FieldType.Double)
    private BigDecimal rating;

    @Field(type = FieldType.Object)
    private Map<String, Object> specifications;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Date)
    private Instant updatedAt;
}
