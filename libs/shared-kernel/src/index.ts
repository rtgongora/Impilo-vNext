/**
 * shared-kernel — Impilo vNext compliance primitives.
 * Companion Spec v1.1.0-canonical.
 *
 * This library is dependency-safe: it MUST NOT import any service code.
 * Services may import shared-kernel; shared-kernel may never import services.
 */

// --- Shared Types ---
export {
  ConsistencyClass,
  Authority,
} from "./types.js";
export type {
  TenantContext,
  ActionType,
  ConsistencyDecision,
} from "./types.js";

// --- ConsistencyGate ---
export { decideConsistency } from "./consistency/index.js";

// --- DualEmitPolicy ---
export {
  resolveEmitMode,
  shouldEmitV1,
  shouldEmitV1_1,
} from "./events/index.js";
export type { EmitMode } from "./events/index.js";

// --- SchemaValidator ---
export {
  SchemaValidationError,
  validateEventEnvelope,
  assertMetaSchemaVersion,
  validateEvent,
} from "./schema/index.js";

// --- AuditLedger ---
export {
  InMemoryAuditLedger,
  computeRecordHash,
  stableStringify,
} from "./audit/index.js";
export type {
  AuditRecord,
  AuditRecordInput,
  AuditDecision,
  AuditSink,
} from "./audit/index.js";
