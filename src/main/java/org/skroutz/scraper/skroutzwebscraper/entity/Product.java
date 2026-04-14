package org.skroutz.scraper.skroutzwebscraper.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Entity
@Table(name = "product", schema = "scraper_schema")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "category", length = 255)
    private String category;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "rating", precision = 3, scale = 2)
    private BigDecimal rating;

    @Column(name = "specifications", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode specifications;

    @Column(name = "reviews_parsed", nullable = false)
    @Builder.Default
    private Boolean reviewsParsed = false;

    @Column(name = "price_history_parsed", nullable = false)
    @Builder.Default
    private Boolean priceHistoryParsed = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Timestamp createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private Timestamp updatedAt;
}