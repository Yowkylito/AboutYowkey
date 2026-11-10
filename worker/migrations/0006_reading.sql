-- How the page is read, as counts per local day and nothing else: how far down visitors get (`section:skills`),
-- which projects they open (`project:vistago`), how long they stay (`time:60`, seconds reached), what they read it
-- on (`device:phone`), where they came from (`source:linkedin`) and which page they got (`view:app`, `view:plain`).
-- An event is counted once per visitor per day; `reading_seen` holds the day's ids for that, the same daily ids as
-- `visitors`, deleted the same way.
CREATE TABLE IF NOT EXISTS reading (
  day   TEXT NOT NULL,               -- local date, YYYY-MM-DD
  event TEXT NOT NULL,               -- kind:value
  count INTEGER NOT NULL DEFAULT 0,  -- distinct visitors that day
  PRIMARY KEY (day, event)
);

CREATE TABLE IF NOT EXISTS reading_seen (
  day     TEXT NOT NULL,
  event   TEXT NOT NULL,
  visitor TEXT NOT NULL,
  PRIMARY KEY (day, event, visitor)
);
