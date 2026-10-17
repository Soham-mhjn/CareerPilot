CREATE TABLE IF NOT EXISTS application_status_history (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    application_id BIGINT NOT NULL REFERENCES job_application(id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_status_history_owner_changed
    ON application_status_history(owner_id, changed_at);
CREATE INDEX IF NOT EXISTS idx_status_history_application_changed
    ON application_status_history(application_id, changed_at);

-- Existing records have no historical transitions. Preserve their known current stage
-- as a baseline event without inventing earlier application history.
INSERT INTO application_status_history(owner_id, application_id, from_status, to_status, changed_at)
SELECT owner_id, id, NULL, status, created_at
FROM job_application;
