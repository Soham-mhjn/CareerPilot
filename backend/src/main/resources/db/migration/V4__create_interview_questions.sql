CREATE TABLE IF NOT EXISTS interview_question (
 id BIGSERIAL PRIMARY KEY, owner_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 application_id BIGINT NOT NULL REFERENCES job_application(id) ON DELETE CASCADE,
 category VARCHAR(255) NOT NULL, difficulty VARCHAR(255) NOT NULL, question VARCHAR(2000) NOT NULL,
 relevance VARCHAR(1000), answer_structure VARCHAR(3000), practice_answer VARCHAR(8000), self_assessment VARCHAR(3000),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_question_owner_app ON interview_question(owner_id,application_id);
