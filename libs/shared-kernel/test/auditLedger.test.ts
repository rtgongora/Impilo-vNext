import { describe, it, expect } from "vitest";
import {
  InMemoryAuditLedger,
  computeRecordHash,
  stableStringify,
} from "../src/audit/auditLedger.js";
import type { AuditRecordInput } from "../src/audit/auditLedger.js";

function makeInput(overrides?: Partial<AuditRecordInput>): AuditRecordInput {
  return {
    record_id: "rec-001",
    occurred_at: "2025-01-01T00:00:00Z",
    tenant_id: "tenant-1",
    pod_id: "pod-1",
    actor_id: "user-42",
    action: "policy.decision",
    subject_type: "patient",
    subject_id: "cpid-123",
    decision: "ALLOW",
    reason_codes: ["R01"],
    policy_version: "v1.0",
    ...overrides,
  };
}

describe("stableStringify", () => {
  it("produces sorted keys", () => {
    const a = stableStringify({ b: 1, a: 2 });
    const b = stableStringify({ a: 2, b: 1 });
    expect(a).toBe(b);
    expect(a).toBe('{"a":2,"b":1}');
  });

  it("handles nested objects with sorted keys", () => {
    const result = stableStringify({ z: { b: 1, a: 2 }, a: 0 });
    expect(result).toBe('{"a":0,"z":{"a":2,"b":1}}');
  });

  it("handles arrays preserving order", () => {
    const result = stableStringify([3, 1, 2]);
    expect(result).toBe("[3,1,2]");
  });

  it("handles null and primitives", () => {
    expect(stableStringify(null)).toBe("null");
    expect(stableStringify("hello")).toBe('"hello"');
    expect(stableStringify(42)).toBe("42");
    expect(stableStringify(true)).toBe("true");
  });
});

describe("InMemoryAuditLedger", () => {
  it("first record has prev_hash = null and a computed record_hash", async () => {
    const ledger = new InMemoryAuditLedger();
    const record = await ledger.append(makeInput());

    expect(record.prev_hash).toBeNull();
    expect(record.record_hash).toBeDefined();
    expect(typeof record.record_hash).toBe("string");
    expect(record.record_hash.length).toBe(64); // SHA-256 hex
  });

  it("second record prev_hash equals first record record_hash", async () => {
    const ledger = new InMemoryAuditLedger();
    const first = await ledger.append(makeInput({ record_id: "rec-001" }));
    const second = await ledger.append(makeInput({ record_id: "rec-002" }));

    expect(second.prev_hash).toBe(first.record_hash);
    expect(second.record_hash).not.toBe(first.record_hash);
  });

  it("list returns all appended records in order", async () => {
    const ledger = new InMemoryAuditLedger();
    await ledger.append(makeInput({ record_id: "rec-001" }));
    await ledger.append(makeInput({ record_id: "rec-002" }));
    await ledger.append(makeInput({ record_id: "rec-003" }));

    const all = await ledger.list();
    expect(all).toHaveLength(3);
    expect(all[0].record_id).toBe("rec-001");
    expect(all[1].record_id).toBe("rec-002");
    expect(all[2].record_id).toBe("rec-003");
  });

  it("hash chain is verifiable (each record_hash matches recomputation)", async () => {
    const ledger = new InMemoryAuditLedger();
    await ledger.append(makeInput({ record_id: "rec-001" }));
    await ledger.append(makeInput({ record_id: "rec-002" }));

    const all = await ledger.list();
    for (const record of all) {
      const { record_hash, ...rest } = record;
      const recomputed = computeRecordHash(rest);
      expect(recomputed).toBe(record_hash);
    }
  });

  it("tamper detection: mutating a stored record causes hash mismatch", async () => {
    const ledger = new InMemoryAuditLedger();
    const original = await ledger.append(makeInput({ record_id: "rec-001" }));

    // Simulate tampering by copying and altering a field
    const tampered = { ...original, action: "policy.tampered" };
    const { record_hash: storedHash, ...restTampered } = tampered;
    const recomputedHash = computeRecordHash(restTampered);

    // The recomputed hash of the tampered record will NOT match
    // the original record_hash, proving tamper detection
    expect(recomputedHash).not.toBe(original.record_hash);
  });

  it("list returns a copy (external mutations do not affect ledger)", async () => {
    const ledger = new InMemoryAuditLedger();
    await ledger.append(makeInput({ record_id: "rec-001" }));

    const listA = await ledger.list();
    listA.pop();
    const listB = await ledger.list();
    expect(listB).toHaveLength(1);
  });
});
