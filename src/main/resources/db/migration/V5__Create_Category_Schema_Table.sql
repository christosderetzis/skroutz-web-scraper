CREATE TABLE scraper_schema.category_schema (
    id         BIGSERIAL    PRIMARY KEY,
    category   VARCHAR(255) NOT NULL,
    schema     JSONB        NOT NULL,
    version    INT          NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ           DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_category_schema_category UNIQUE (category)
);
