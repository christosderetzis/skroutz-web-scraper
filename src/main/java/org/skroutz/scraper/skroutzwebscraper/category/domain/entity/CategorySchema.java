package org.skroutz.scraper.skroutzwebscraper.category.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.CategoryMappingSchema;

import java.sql.Timestamp;

@Entity
@Table(name = "category_schema", schema = "scraper_schema")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategorySchema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category", unique = true, nullable = false)
    private String category;

    @Column(name = "schema", columnDefinition = "JSONB", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private CategoryMappingSchema schema;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private int version = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Timestamp createdAt;
}
