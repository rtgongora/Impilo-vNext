/**
 * AuditLedger — Companion Spec 4.3 tamper-evident append-only ledger.
 *
 * Provides an in-memory AuditSink implementation with SHA-256 hash chaining.
 * Dependency-safe: uses only Node built-in crypto.
 */
import { createHash } from "node:crypto";

// --- Types ---

export type AuditDecision =
  | "ALLOW"
  | "DENY"
  | "BREAK_GLASS_REQUIRED"
  | "STEP_UP_REQUIRED";

export interface AuditRecord {
  record_id: string;
  occurred_at: string;
  tenant_id: string;
  pod_id: string;
  actor_id: string;
  action: string;
  subject_type?: string;
  subject_id?: string;
  decision?: AuditDecision;
  reason_codes?: string[];
  policy_version?: string;
  context?: any;
  prev_hash: string | null;
  record_hash: string;
}

/** Input type for append — excludes computed hash fields. */
export type AuditRecordInput = Omit<AuditRecord, "prev_hash" | "record_hash">;

export interface AuditSink {
  append(record: AuditRecordInput): Promise<AuditRecord>;
  list(): Promise<AuditRecord[]>;
}

// --- Stable JSON stringify ---

/**
 * Deterministic JSON stringify with sorted keys.
 * Ensures hash stability regardless of property insertion order.
 */
export function stableStringify(value: unknown): string {
  if (value === null || value === undefined) {
    return JSON.stringify(value);
  }

  if (typeof value !== "object") {
    return JSON.stringify(value);
  }

  if (Array.isArray(value)) {
    const items = value.map((item) => stableStringify(item));
    return `[${items.join(",")}]`;
  }

  const obj = value as Record<string, unknown>;
  const keys = Object.keys(obj).sort();
  const pairs = keys
    .filter((k) => obj[k] !== undefined)
    .map((k) => `${JSON.stringify(k)}:${stableStringify(obj[k])}`);
  return `{${pairs.join(",")}}`;
}

// --- Hash computation ---

/**
 * Compute SHA-256 of a record, including prev_hash but excluding record_hash.
 */
export function computeRecordHash(
  record: Omit<AuditRecord, "record_hash">,
): string {
  const canonical = stableStringify(record);
  return createHash("sha256").update(canonical).digest("hex");
}

// --- InMemoryAuditLedger ---

export class InMemoryAuditLedger implements AuditSink {
  private readonly records: AuditRecord[] = [];

  async append(input: AuditRecordInput): Promise<AuditRecord> {
    const lastRecord =
      this.records.length > 0
        ? this.records[this.records.length - 1]
        : null;

    const prev_hash = lastRecord ? lastRecord.record_hash : null;

    const withPrevHash: Omit<AuditRecord, "record_hash"> = {
      ...input,
      prev_hash,
    };

    const record_hash = computeRecordHash(withPrevHash);

    const record: AuditRecord = {
      ...withPrevHash,
      record_hash,
    };

    this.records.push(record);
    return record;
  }

  async list(): Promise<AuditRecord[]> {
    return [...this.records];
  }
}
