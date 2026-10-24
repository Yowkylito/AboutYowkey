-- Wrong guesses at the owner's password (ADMIN_TOKEN), per address per 15 minutes: after 10, the inbox and the app's
-- routes turn that address away until the window ends. A guess is counted before it is checked, and given back when
-- it was right. The address is a salted hash, and a window's rows are deleted once it is over.
CREATE TABLE IF NOT EXISTS owner_tries (
  client TEXT NOT NULL,              -- hash of the address's network and VISITOR_SALT, never the address
  since  TEXT NOT NULL,              -- the start of the 15-minute window, ISO 8601 UTC
  count  INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (client, since)
);
