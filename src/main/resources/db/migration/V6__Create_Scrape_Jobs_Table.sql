CREATE TABLE scraper_schema.scrape_jobs (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    job_type    VARCHAR(50)  NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    started_at  TIMESTAMPTZ  NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ,
    error       TEXT
);

-- Drops the old per-type index
DROP INDEX IF EXISTS scraper_schema.idx_scrape_jobs_one_running_per_type;

-- Enforces exactly ONE job running globally across the entire table
CREATE UNIQUE INDEX idx_scrape_jobs_one_running_global
    ON scraper_schema.scrape_jobs (status)
    WHERE status = 'RUNNING';
