-- How a note's writer knows the owner, and the owner's pins.

-- What the note form asks, optionally: colleague, friend, special or other (the worker's RELATIONS, which it checks
-- before a note is kept). Null is "did not say", as every note kept before this is: those count as other in the
-- star sheets' sorter and filters, and are left null here so that saying "other" and saying nothing stay apart.
ALTER TABLE feedback ADD COLUMN relation TEXT;

-- When the owner pinned the note: pinned notes come first on the site, the newest pin first. Only a note on the site
-- can be pinned, and hiding one unpins it, so a pin never outlives the note being public.
ALTER TABLE feedback ADD COLUMN pinned_at TEXT;
CREATE INDEX IF NOT EXISTS feedback_pinned ON feedback (pinned_at);
