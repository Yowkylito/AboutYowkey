import { DatabaseSync } from "node:sqlite";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

/**
 * D1's binding API (prepare → bind → first / all / run, and batch) over Node's own SQLite, so handler.js runs
 * unchanged in the tests, against the real migrations in order, with real SQL semantics (ON CONFLICT, INSERT OR
 * IGNORE, changes()).
 */
export function openDatabase(migrationsDir) {
  const db = new DatabaseSync(":memory:");
  for (const file of readdirSync(migrationsDir).filter((name) => name.endsWith(".sql")).sort()) {
    db.exec(readFileSync(join(migrationsDir, file), "utf8"));
  }
  const totalChanges = db.prepare("SELECT total_changes() AS n, last_insert_rowid() AS last_row_id");
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
        /** What batch() runs: the rows, if any, and what it changed, as D1 answers each statement of a batch. */
        execute: () => {
          const before = totalChanges.get().n;
          const results = statement.all(...params);
          const { n, last_row_id } = totalChanges.get();
          return { success: true, results, meta: { changes: n - before, last_row_id } };
        },
      });
      return { ...bound([]), bind: (...params) => bound(params) };
    },
    /** As D1: the statements in order, in one transaction, rolled back whole if one fails. */
    async batch(statements) {
      db.exec("BEGIN");
      try {
        const results = statements.map((statement) => statement.execute());
        db.exec("COMMIT");
        return results;
      } catch (error) {
        db.exec("ROLLBACK");
        throw error;
      }
    },
    /** Straight SQL for the tests' own assertions. */
    raw: db,
  };
}
