CREATE SCHEMA IF NOT EXISTS scraper_schema;

CREATE TABLE scraper_schema.product (
    id BIGSERIAL PRIMARY KEY,
    url VARCHAR(2048) NOT NULL,
    title VARCHAR(500) NOT NULL,
    price DECIMAL(10,2),
    image_url VARCHAR(2048),
    description TEXT,
    rating DECIMAL(3,2) CHECK (rating >= 0 AND rating <= 5),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_url ON scraper_schema.product(url);
CREATE INDEX idx_product_title ON scraper_schema.product(title);
CREATE INDEX idx_product_price ON scraper_schema.product(price);
CREATE INDEX idx_product_rating ON scraper_schema.product(rating);