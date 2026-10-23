import { DatabaseSync } from "node:sqlite";
import { readFileSync } from "node:fs";

/**
 * D1's binding API (prepare → bind → first / all / run) over Node's own SQLite, so handler.js runs unchanged in
 * the tests, against the real schema, with real SQL semantics (ON CONFLICT, INSERT OR IGNORE, changes()).
 */
export function openDatabase(schemaPath) {
  const db = new DatabaseSync(":memory:");
  db.exec(readFileSync(schemaPath, "utf8"));
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
