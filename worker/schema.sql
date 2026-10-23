-- The portfolio's data: visit counts and the feedback inbox. Apply with `npm run db:init` (README, "Visits and feedback").
-- Every statement is idempotent, so it can be re-run after adding a table or an index.

CREATE TABLE IF NOT EXISTS visits (
  day   TEXT PRIMARY KEY,            -- UTC date, YYYY-MM-DD
  count INTEGER NOT NULL DEFAULT 0   -- distinct visitors that day
);

-- Who has been counted today. The id is a hash of the day, the address, the browser and a secret salt: the
-- address itself is never stored, and the rows of earlier days are deleted as soon as a new day is counted.
CREATE TABLE IF NOT EXISTS visitors (
  day     TEXT NOT NULL,
  visitor TEXT NOT NULL,
  PRIMARY KEY (day, visitor)
);

CREATE TABLE IF NOT EXISTS feedback (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  created_at TEXT NOT NULL,          -- ISO 8601, UTC
  name       TEXT,                   -- optional
  contact    TEXT,                   -- optional: an email, or whatever they left for a reply
  rating     INTEGER CHECK (rating BETWEEN 1 AND 5),  -- optional overall impression
  message    TEXT NOT NULL,
  visitor    TEXT NOT NULL           -- the same daily id, for the per-visitor limit
);

CREATE INDEX IF NOT EXISTS feedback_visitor ON feedback (visitor);
