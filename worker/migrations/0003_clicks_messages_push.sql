-- Link clicks, countries, private messages, and the owner's alerts (records, phones, the daily report).
-- From here on everything is counted by the owner's local day (TIME_ZONE in wrangler.toml, Asia/Manila), not UTC.
-- The `visits` rows written before this keep their UTC keys: a day near midnight may be split, nothing is lost.

-- Visits by country, as a count per day and nothing else: Cloudflare's guess from the address ("XX" when it has
-- none), never tied to a visitor.
CREATE TABLE IF NOT EXISTS visit_countries (
  day     TEXT NOT NULL,                -- local date, YYYY-MM-DD
  country TEXT NOT NULL,                -- ISO 3166 alpha-2, or "XX"
  count   INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (day, country)
);

-- Clicks on the links the page reports (TRACKED_LINKS), one per visitor per day per link.
CREATE TABLE IF NOT EXISTS clicks (
  day   TEXT NOT NULL,
  link  TEXT NOT NULL,
  count INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (day, link)
);

-- Who has clicked which link today: the same daily hash as `visitors`, and deleted the same way once a new day's
-- click is counted.
CREATE TABLE IF NOT EXISTS clickers (
  day     TEXT NOT NULL,
  link    TEXT NOT NULL,
  visitor TEXT NOT NULL,
  PRIMARY KEY (day, link, visitor)
);

-- Private messages to the owner. Never shown on the site; the contact is required, since a message wants a reply.
CREATE TABLE IF NOT EXISTS messages (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  created_at  TEXT NOT NULL,            -- ISO 8601, UTC
  day         TEXT NOT NULL,            -- the local date it arrived, for the counts by day
  name        TEXT,                     -- optional
  contact     TEXT NOT NULL,            -- an email, or whatever they left for a reply
  topic       TEXT NOT NULL DEFAULT 'other' CHECK (topic IN ('job', 'freelance', 'collaboration', 'other')),
  message     TEXT NOT NULL,
  visitor     TEXT NOT NULL,            -- the daily id, for the per-visitor limit
  read_at     TEXT,
  archived_at TEXT
);

CREATE INDEX IF NOT EXISTS messages_visitor ON messages (visitor);
CREATE INDEX IF NOT EXISTS messages_day ON messages (day);

-- Every alert the owner gets, kept first as a record: the app's push shows it, and its backup poll reads the rows
-- after the last id it has seen, so a lost push is still seen.
CREATE TABLE IF NOT EXISTS notifications (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  created_at TEXT NOT NULL,
  kind       TEXT NOT NULL CHECK (kind IN ('feedback', 'message', 'daily')),
  title      TEXT NOT NULL,
  body       TEXT NOT NULL,
  ref_id     INTEGER                    -- the note's or the message's id; null for the daily report
);

-- The owner's signed-in phones: their FCM tokens. A token FCM no longer knows is deleted when a push is refused.
CREATE TABLE IF NOT EXISTS devices (
  token        TEXT PRIMARY KEY,
  platform     TEXT NOT NULL DEFAULT 'android',
  created_at   TEXT NOT NULL,
  last_seen_at TEXT NOT NULL
);

-- The 23:59 report, one row per local day; a second run of the cron the same day rewrites it.
CREATE TABLE IF NOT EXISTS daily_reports (
  day        TEXT PRIMARY KEY,
  visits     INTEGER NOT NULL,
  clicks     TEXT NOT NULL,             -- JSON: { "linkedin": 2, "github": 1 }
  notes      INTEGER NOT NULL,
  messages   INTEGER NOT NULL,
  created_at TEXT NOT NULL
);

-- Notes are counted by local day too. A column, rather than computing it from created_at on every read, so the
-- day a note counted on never moves if TIME_ZONE changes. The notes already kept get theirs from created_at shifted
-- to Asia/Manila (UTC+8 all year, no daylight saving); new notes get it from the worker.
ALTER TABLE feedback ADD COLUMN day TEXT;
UPDATE feedback SET day = date(created_at, '+8 hours') WHERE day IS NULL;
CREATE INDEX IF NOT EXISTS feedback_day ON feedback (day);
