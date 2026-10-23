-- A note may be shown on the site: only with the visitor's consent, and once the owner publishes it from the inbox.
-- The owner may also hide one (spam, say) without deleting it.
ALTER TABLE feedback ADD COLUMN consent INTEGER NOT NULL DEFAULT 0;
ALTER TABLE feedback ADD COLUMN published_at TEXT;
ALTER TABLE feedback ADD COLUMN hidden_at TEXT;
CREATE INDEX IF NOT EXISTS feedback_published ON feedback (published_at);
