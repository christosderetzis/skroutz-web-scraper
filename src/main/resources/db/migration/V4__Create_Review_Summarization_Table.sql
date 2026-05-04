CREATE TABLE scraper_schema.review_summary (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    summary TEXT,
    pros TEXT[],
    cons TEXT[],
    sentiment VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_summary_product_id FOREIGN KEY (product_id)
        REFERENCES scraper_schema.product(id) ON DELETE CASCADE,
    CONSTRAINT uq_review_summary_product_id UNIQUE (product_id)
);
