import { DatabaseSync } from "node:sqlite";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

/**
 * D1's binding API (prepare → bind → first / all / run) over Node's own SQLite, so handler.js runs unchanged in
 * the tests, against the real migrations in order, with real SQL semantics (ON CONFLICT, INSERT OR IGNORE, changes()).
 */
export function openDatabase(migrationsDir) {
  const db = new DatabaseSync(":memory:");
  for (const file of readdirSync(migrationsDir).filter((name) => name.endsWith(".sql")).sort()) {
    db.exec(readFileSync(join(migrationsDir, file), "utf8"));
  }
  return {
    prepare(sql) {
      const statement = db.prepare(sql);
      const bound = (params) => ({
        first: async (column) => {
          const row = statement.get(...params) ?? null;
          return column && row ? row[column] : row;
        },
        all: async () => ({ success: true, results: statement.all(...params) }),
        run: async () => {
          const result = statement.run(...params);
          return { success: true, meta: { changes: result.changes, last_row_id: result.lastInsertRowid } };
        },
      });
      return { ...bound([]), bind: (...params) => bound(params) };
    },
    /** Straight SQL for the tests' own assertions. */
    raw: db,
  };
}
