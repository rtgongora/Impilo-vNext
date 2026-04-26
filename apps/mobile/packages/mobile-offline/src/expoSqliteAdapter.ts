/**
 * Expo SQLite-backed {@link LocalStorageAdapter} — durable offline queue + records.
 *
 * Loaded lazily so Vitest / Node environments without `expo-sqlite` never import the native module.
 */

import type {
  ConflictRecord,
  ConflictResolution,
  LocalStorageAdapter,
  OfflineRecord,
  QueuedOperation,
} from "./types";

type ExpoSqlDatabase = {
  execAsync: (sql: string) => Promise<void>;
  runAsync: (sql: string, ...params: unknown[]) => Promise<{ lastInsertRowId: number; changes: number }>;
  getFirstAsync: <T>(sql: string, ...params: unknown[]) => Promise<T | null>;
  getAllAsync: <T>(sql: string, ...params: unknown[]) => Promise<T[]>;
};

const SCHEMA = `
CREATE TABLE IF NOT EXISTS offline_records (
  collection TEXT NOT NULL,
  id TEXT NOT NULL,
  doc TEXT NOT NULL,
  PRIMARY KEY (collection, id)
);
CREATE TABLE IF NOT EXISTS offline_queue (
  id TEXT PRIMARY KEY,
  doc TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS offline_conflicts (
  id TEXT PRIMARY KEY,
  doc TEXT NOT NULL
);
`;

export class ExpoSqliteStorageAdapter implements LocalStorageAdapter {
  constructor(private readonly db: ExpoSqlDatabase) {}

  static async open(databaseName: string): Promise<ExpoSqliteStorageAdapter> {
    const mod = await import("expo-sqlite");
    const db = (await mod.openDatabaseAsync(databaseName)) as ExpoSqlDatabase;
    const adapter = new ExpoSqliteStorageAdapter(db);
    await adapter.initSchema();
    return adapter;
  }

  async initSchema(): Promise<void> {
    await this.db.execAsync(SCHEMA);
  }

  async get<T>(collection: string, id: string): Promise<OfflineRecord<T> | null> {
    const row = await this.db.getFirstAsync<{ doc: string }>(
      "SELECT doc FROM offline_records WHERE collection = ? AND id = ?",
      collection,
      id
    );
    if (!row) return null;
    return JSON.parse(row.doc) as OfflineRecord<T>;
  }

  async getAll<T>(collection: string): Promise<OfflineRecord<T>[]> {
    const rows = await this.db.getAllAsync<{ doc: string }>(
      "SELECT doc FROM offline_records WHERE collection = ?",
      collection
    );
    return rows.map((r) => JSON.parse(r.doc) as OfflineRecord<T>);
  }

  async query<T>(
    collection: string,
    predicate: (item: OfflineRecord<T>) => boolean
  ): Promise<OfflineRecord<T>[]> {
    const all = await this.getAll<T>(collection);
    return all.filter(predicate);
  }

  async put<T>(record: OfflineRecord<T>): Promise<void> {
    await this.db.runAsync(
      "INSERT OR REPLACE INTO offline_records (collection, id, doc) VALUES (?, ?, ?)",
      record.collection,
      record.id,
      JSON.stringify(record)
    );
  }

  async delete(collection: string, id: string): Promise<void> {
    await this.db.runAsync("DELETE FROM offline_records WHERE collection = ? AND id = ?", collection, id);
  }

  async clear(collection: string): Promise<void> {
    await this.db.runAsync("DELETE FROM offline_records WHERE collection = ?", collection);
  }

  async clearAll(): Promise<void> {
    await this.db.execAsync("DELETE FROM offline_records; DELETE FROM offline_queue; DELETE FROM offline_conflicts;");
  }

  async enqueue(op: QueuedOperation): Promise<void> {
    await this.db.runAsync("INSERT OR REPLACE INTO offline_queue (id, doc) VALUES (?, ?)", op.id, JSON.stringify(op));
  }

  async dequeue(): Promise<QueuedOperation | null> {
    await this.db.execAsync("BEGIN IMMEDIATE");
    try {
      const row = await this.db.getFirstAsync<{ id: string; doc: string }>(
        `SELECT id, doc FROM offline_queue
         WHERE json_extract(doc, '$.status') = 'pending'
         ORDER BY json_extract(doc, '$.createdAt') ASC
         LIMIT 1`
      );
      if (!row) {
        await this.db.execAsync("COMMIT");
        return null;
      }
      const op = JSON.parse(row.doc) as QueuedOperation;
      op.status = "in_flight";
      await this.db.runAsync("UPDATE offline_queue SET doc = ? WHERE id = ?", JSON.stringify(op), row.id);
      await this.db.execAsync("COMMIT");
      return op;
    } catch (e) {
      await this.db.execAsync("ROLLBACK");
      throw e;
    }
  }

  async peekQueue(): Promise<QueuedOperation[]> {
    const rows = await this.db.getAllAsync<{ doc: string }>(
      `SELECT doc FROM offline_queue
       WHERE json_extract(doc, '$.status') IN ('pending', 'in_flight')
       ORDER BY json_extract(doc, '$.createdAt') ASC`
    );
    return rows.map((r) => JSON.parse(r.doc) as QueuedOperation);
  }

  async listQueue(): Promise<QueuedOperation[]> {
    const rows = await this.db.getAllAsync<{ doc: string }>(
      "SELECT doc FROM offline_queue ORDER BY json_extract(doc, '$.createdAt') ASC"
    );
    return rows.map((r) => JSON.parse(r.doc) as QueuedOperation);
  }

  async resetStaleInFlight(reason: string = "reset_stale_in_flight"): Promise<void> {
    const rows = await this.db.getAllAsync<{ id: string; doc: string }>("SELECT id, doc FROM offline_queue");
    for (const row of rows) {
      const op = JSON.parse(row.doc) as QueuedOperation;
      if (op.status === "in_flight") {
        op.status = "pending";
        op.error = reason;
        await this.db.runAsync("UPDATE offline_queue SET doc = ? WHERE id = ?", JSON.stringify(op), row.id);
      }
    }
  }

  async updateQueueItem(id: string, update: Partial<QueuedOperation>): Promise<void> {
    const row = await this.db.getFirstAsync<{ doc: string }>("SELECT doc FROM offline_queue WHERE id = ?", id);
    if (!row) return;
    const op = JSON.parse(row.doc) as QueuedOperation;
    Object.assign(op, update);
    await this.db.runAsync("UPDATE offline_queue SET doc = ? WHERE id = ?", JSON.stringify(op), id);
  }

  async removeQueueItem(id: string): Promise<void> {
    await this.db.runAsync("DELETE FROM offline_queue WHERE id = ?", id);
  }

  async getQueueSize(): Promise<number> {
    const row = await this.db.getFirstAsync<{ c: number }>(
      `SELECT COUNT(*) as c FROM offline_queue
       WHERE json_extract(doc, '$.status') IN ('pending', 'in_flight')`
    );
    return row?.c ?? 0;
  }

  async addConflict<T>(conflict: ConflictRecord<T>): Promise<void> {
    await this.db.runAsync(
      "INSERT OR REPLACE INTO offline_conflicts (id, doc) VALUES (?, ?)",
      conflict.id,
      JSON.stringify(conflict)
    );
  }

  async getConflicts(): Promise<ConflictRecord[]> {
    const rows = await this.db.getAllAsync<{ doc: string }>("SELECT doc FROM offline_conflicts");
    return rows.map((r) => JSON.parse(r.doc) as ConflictRecord);
  }

  async resolveConflict(id: string, resolution: ConflictResolution): Promise<void> {
    const row = await this.db.getFirstAsync<{ doc: string }>("SELECT doc FROM offline_conflicts WHERE id = ?", id);
    if (!row) return;
    const c = JSON.parse(row.doc) as ConflictRecord;
    c.resolution = resolution;
    await this.db.runAsync("UPDATE offline_conflicts SET doc = ? WHERE id = ?", JSON.stringify(c), id);
  }

  async removeConflict(id: string): Promise<void> {
    await this.db.runAsync("DELETE FROM offline_conflicts WHERE id = ?", id);
  }
}

/**
 * Open a durable offline store backed by Expo SQLite (persists across app restarts).
 */
export async function openExpoSqliteOfflineAdapter(databaseName: string): Promise<ExpoSqliteStorageAdapter> {
  return ExpoSqliteStorageAdapter.open(databaseName);
}
