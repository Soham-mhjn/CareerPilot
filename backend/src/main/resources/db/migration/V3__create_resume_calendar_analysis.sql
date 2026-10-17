CREATE TABLE IF NOT EXISTS resume_document (
 id BIGSERIAL PRIMARY KEY, owner_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 file_name VARCHAR(255) NOT NULL, storage_name VARCHAR(255) NOT NULL, version_name VARCHAR(255) NOT NULL,
 target_role VARCHAR(300), extracted_text VARCHAR(100000), file_size BIGINT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_resume_owner ON resume_document(owner_id);
CREATE TABLE IF NOT EXISTS workspace_item (
 id BIGSERIAL PRIMARY KEY, owner_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 type VARCHAR(30) NOT NULL, title VARCHAR(255) NOT NULL, notes VARCHAR(5000), company VARCHAR(255),
 starts_at TIMESTAMP WITH TIME ZONE, duration_minutes INTEGER, meeting_url VARCHAR(2000), completed BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_workspace_owner ON workspace_item(owner_id);
CREATE TABLE IF NOT EXISTS job_analysis (
 id BIGSERIAL PRIMARY KEY, owner_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 application_id BIGINT NOT NULL REFERENCES job_application(id) ON DELETE CASCADE, score INTEGER NOT NULL,
 result_json VARCHAR(30000), created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_analysis_application ON job_analysis(owner_id,application_id,created_at DESC);

