CREATE TABLE scraper_schema.reviews (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    reviewer_name VARCHAR(255),
    reviewer_rating INTEGER NOT NULL CHECK (reviewer_rating >= 1 AND reviewer_rating <= 5),
    review_date DATE,
    helpful_votes INTEGER DEFAULT 0 CHECK (helpful_votes >= 0),
    total_votes INTEGER DEFAULT 0 CHECK (total_votes >= 0),
    review_text TEXT,
    pros TEXT[],
    cons TEXT[],
    neutral TEXT[],
    is_verified_purchase BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_reviews_product_id 
        FOREIGN KEY (product_id) 
        REFERENCES scraper_schema.product(id) 
        ON DELETE CASCADE
);

CREATE INDEX idx_reviews_product_id ON scraper_schema.reviews(product_id);
CREATE INDEX idx_reviews_rating ON scraper_schema.reviews(reviewer_rating);
CREATE INDEX idx_reviews_date ON scraper_schema.reviews(review_date);
CREATE INDEX idx_reviews_verified ON scraper_schema.reviews(is_verified_purchase);