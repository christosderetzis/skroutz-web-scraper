CREATE TABLE scraper_schema.price_history (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    price_date TIMESTAMPTZ NOT NULL,
    store_name VARCHAR(255),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_reviews_product_id
      FOREIGN KEY (product_id)
      REFERENCES scraper_schema.product(id)
      ON DELETE CASCADE
);