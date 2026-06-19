package org.skroutz.scraper.skroutzwebscraper.review.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product;

import java.sql.Timestamp;
import java.time.LocalDate;

@Entity
@Table(name = "reviews", schema = "scraper_schema")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "reviewer_name", length = 255)
    private String reviewerName;

    @Column(name = "reviewer_rating")
    private Integer reviewerRating;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Column(name = "helpful_votes")
    private Integer helpfulVotes;

    @Column(name = "total_votes")
    private Integer totalVotes;

    @Column(name = "review_text", columnDefinition = "TEXT")
    private String reviewText;

    @Column(name = "pros", columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] pros;

    @Column(name = "cons", columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] cons;

    @Column(name = "neutral", columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] neutral;

    @Column(name = "is_verified_purchase")
    @Builder.Default
    private Boolean isVerifiedPurchase = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private Product product;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Timestamp createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private Timestamp updatedAt;
}