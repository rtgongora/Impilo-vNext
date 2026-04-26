/**
 * In-Memory Local Storage Adapter — For testing and environments without SQLite.
 *
 * Production apps should replace this with a SQLite-backed adapter.
 */

import type {
  LocalStorageAdapter,
  OfflineRecord,
  QueuedOperation,
  ConflictRecord,
  ConflictResolution,
} from "./types";

export class MemoryStorageAdapter implements LocalStorageAdapter {
  private collections = new Map<string, Map<string, OfflineRecord>>();
  private queue: QueuedOperation[] = [];
  private conflicts = new Map<string, ConflictRecord>();

  private getCollection(name: string): Map<string, OfflineRecord> {
    let col = this.collections.get(name);
    if (!col) {
      col = new Map();
      this.collections.set(name, col);
    }
    return col;
  }

  async get<T>(collection: string, id: string): Promise<OfflineRecord<T> | null> {
    return (this.getCollection(collection).get(id) as OfflineRecord<T>) ?? null;
  }

  async getAll<T>(collection: string): Promise<OfflineRecord<T>[]> {
    return Array.from(this.getCollection(collection).values()) as OfflineRecord<T>[];
  }

  async query<T>(
    collection: string,
    predicate: (item: OfflineRecord<T>) => boolean
  ): Promise<OfflineRecord<T>[]> {
    const all = await this.getAll<T>(collection);
    return all.filter(predicate);
  }

  async put<T>(record: OfflineRecord<T>): Promise<void> {
    this.getCollection(record.collection).set(record.id, record as OfflineRecord);
  }

  async delete(collection: string, id: string): Promise<void> {
    this.getCollection(collection).delete(id);
  }

  async clear(collection: string): Promise<void> {
    this.collections.delete(collection);
  }

  async clearAll(): Promise<void> {
    this.collections.clear();
    this.queue = [];
    this.conflicts.clear();
  }

  async enqueue(op: QueuedOperation): Promise<void> {
    this.queue.push(op);
  }

  async dequeue(): Promise<QueuedOperation | null> {
    const pending = this.queue.find((op) => op.status === "pending");
    if (pending) {
      pending.status = "in_flight";
      return pending;
    }
    return null;
  }

  async peekQueue(): Promise<QueuedOperation[]> {
    return this.queue.filter((op) => op.status === "pending" || op.status === "in_flight");
  }

  async updateQueueItem(id: string, update: Partial<QueuedOperation>): Promise<void> {
    const item = this.queue.find((op) => op.id === id);
    if (item) {
      Object.assign(item, update);
    }
  }

  async removeQueueItem(id: string): Promise<void> {
    this.queue = this.queue.filter((op) => op.id !== id);
  }

  async listQueue(): Promise<QueuedOperation[]> {
    return this.queue.map((op) => ({ ...op }));
  }

  async resetStaleInFlight(reason: string = "reset_stale_in_flight"): Promise<void> {
    for (const op of this.queue) {
      if (op.status === "in_flight") {
        op.status = "pending";
        op.error = reason;
      }
    }
  }

  async getQueueSize(): Promise<number> {
    return this.queue.filter((op) => op.status === "pending" || op.status === "in_flight").length;
  }

  async addConflict<T>(conflict: ConflictRecord<T>): Promise<void> {
    this.conflicts.set(conflict.id, conflict as ConflictRecord);
  }

  async getConflicts(): Promise<ConflictRecord[]> {
    return Array.from(this.conflicts.values());
  }

  async resolveConflict(id: string, resolution: ConflictResolution): Promise<void> {
    const conflict = this.conflicts.get(id);
    if (conflict) {
      conflict.resolution = resolution;
    }
  }

  async removeConflict(id: string): Promise<void> {
    this.conflicts.delete(id);
  }
}
