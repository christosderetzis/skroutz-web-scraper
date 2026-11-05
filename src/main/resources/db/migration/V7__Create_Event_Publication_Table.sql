CREATE TABLE scraper_schema.event_publication (
    id UUID NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    listener_id VARCHAR(255) NOT NULL,
    serialized_event TEXT NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    completion_date TIMESTAMP WITH TIME ZONE,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    completion_attempts INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(255),
    PRIMARY KEY (id)
);

CREATE INDEX idx_event_publication_completion_date ON scraper_schema.event_publication (completion_date);
