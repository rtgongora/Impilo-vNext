import { apiClient } from "@impilo/mobile-api-client";
import { getOfflineStorage, syncEngine, type OfflineRecord, type QueuedOperation } from "@impilo/mobile-offline";

export type CoverageCommandKind = "eligibility-check" | "claim-submission" | "preauth-request" | "appeal-submission";
export type CoverageCommandPayload = Record<string, unknown>;

export interface CoverageCommandQueueEntry {
  localId: string;
  command: CoverageCommandKind;
  payload: CoverageCommandPayload;
  status: "PENDING" | "FAILED";
  queuedAt: string;
  lastError?: string;
  retryCount: number;
  history: Array<{ at: string; status: "QUEUED" | "SYNCED" | "FAILED"; message: string }>;
}

export interface CoverageCommandResult {
  syncStatus: "SYNCED" | "PROVISIONAL";
  data?: unknown;
  queueEntry?: CoverageCommandQueueEntry;
}

export interface CoveragePayerJourney {
  claims: unknown[];
  remittances: unknown[];
  appeals: unknown[];
  settlements: unknown[];
  reconciliation: Array<{ stage: string; status: "COMPLETE" | "PENDING" | "ACTION_REQUIRED"; detail: string }>;
}

const COMMAND_PATHS: Record<CoverageCommandKind, string> = {
  "eligibility-check": "/internal/v1/coverage/eligibility/check",
  "claim-submission": "/internal/v1/coverage/claims",
  "preauth-request": "/internal/v1/coverage/preauth",
  "appeal-submission": "/internal/v1/appeals",
};

const queue: CoverageCommandQueueEntry[] = [];
const subscribers = new Set<(snapshot: CoverageCommandQueueEntry[]) => void>();

function notify() {
  const snapshot = queue.slice();
  for (const subscriber of subscribers) subscriber(snapshot);
}

function localId() {
  return `coverage-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

async function enqueueDurable(entry: CoverageCommandQueueEntry) {
  try {
    const storage = getOfflineStorage();
    const now = new Date().toISOString();
    const record: OfflineRecord<CoverageCommandQueueEntry> = {
      id: entry.localId,
      collection: "coverage-commands",
      data: entry,
      version: 1,
      localVersion: 1,
      serverVersion: 0,
      status: "pending",
      createdAt: entry.queuedAt,
      updatedAt: now,
    };
    const operation: QueuedOperation = {
      id: `op-${entry.localId}`,
      type: "CREATE",
      collection: "coverage-commands",
      recordId: entry.localId,
      payload: entry.payload,
      method: "POST",
      path: COMMAND_PATHS[entry.command],
      createdAt: now,
      retryCount: 0,
      maxRetries: 5,
      status: "pending",
      idempotencyKey: entry.localId,
    };
    await storage.put(record);
    await storage.enqueue(operation);
  } catch {
    // Tests and web previews may not configure durable storage; the in-memory queue still gives provisional UX.
  }
}

export function subscribeCoverageCommandQueue(cb: (snapshot: CoverageCommandQueueEntry[]) => void) {
  subscribers.add(cb);
  cb(queue.slice());
  return () => {
    subscribers.delete(cb);
  };
}

export function pendingCoverageCommandCount() {
  return queue.length;
}

export async function runCoverageCommand(
  command: CoverageCommandKind,
  payload: CoverageCommandPayload,
): Promise<CoverageCommandResult> {
  try {
    const response = await apiClient.post(COMMAND_PATHS[command], payload);
    return { syncStatus: "SYNCED", data: response.data };
  } catch (error) {
    const entry: CoverageCommandQueueEntry = {
      localId: localId(),
      command,
      payload,
      status: "PENDING",
      queuedAt: new Date().toISOString(),
      lastError: error instanceof Error ? error.message : "Coverage command failed before sync.",
      retryCount: 0,
      history: [
        {
          at: new Date().toISOString(),
          status: "QUEUED",
          message: error instanceof Error ? error.message : "Queued for sync.",
        },
      ],
    };
    queue.push(entry);
    void enqueueDurable(entry);
    notify();
    return { syncStatus: "PROVISIONAL", queueEntry: entry };
  }
}

export async function flushCoverageCommandQueue() {
  try {
    const before = (await getOfflineStorage().listQueue()).filter((item) => item.collection === "coverage-commands");
    await syncEngine.forcePush();
    const remaining = (await getOfflineStorage().listQueue()).filter((item) => item.collection === "coverage-commands");
    const remainingIds = new Set(remaining.map((item) => item.recordId));
    for (let index = queue.length - 1; index >= 0; index -= 1) {
      if (!remainingIds.has(queue[index].localId)) {
        queue.splice(index, 1);
      }
    }
    notify();
    return {
      synced: Math.max(0, before.length - remaining.length),
      remaining: remaining.length,
      failed: remaining.filter((item) => item.status === "failed").length,
    };
  } catch {
    // Fall through to direct in-memory retry when the shared offline engine is unavailable.
  }

  let synced = 0;
  let failed = 0;

  while (queue.length > 0) {
    const entry = queue[0];
    try {
      await apiClient.post(COMMAND_PATHS[entry.command], entry.payload);
      queue.shift();
      synced += 1;
    } catch (error) {
      entry.status = "FAILED";
      entry.lastError = error instanceof Error ? error.message : "Coverage command sync failed.";
      entry.retryCount += 1;
      entry.history.push({
        at: new Date().toISOString(),
        status: "FAILED",
        message: entry.lastError,
      });
      failed += 1;
      break;
    }
  }

  notify();
  return { synced, remaining: queue.length, failed };
}

function rows(value: unknown): unknown[] {
  if (Array.isArray(value)) return value;
  if (value && typeof value === "object") {
    const record = value as Record<string, unknown>;
    if (Array.isArray(record.data)) return record.data;
    if (Array.isArray(record.items)) return record.items;
    if (Array.isArray(record.content)) return record.content;
  }
  return [];
}

async function getRows(path: string): Promise<unknown[]> {
  try {
    const response = await apiClient.get(path);
    return rows(response.data);
  } catch {
    return [];
  }
}

export async function fetchCoveragePayerJourney(input: {
  coverageId?: string;
  memberCpid?: string;
  appellantId?: string;
  intentId?: string;
}): Promise<CoveragePayerJourney> {
  const claimsQuery = input.memberCpid
    ? `member_cpid=${encodeURIComponent(input.memberCpid)}`
    : input.coverageId
      ? `coverageId=${encodeURIComponent(input.coverageId)}`
      : "";
  const appealsQuery = input.appellantId ? `?appellant_id=${encodeURIComponent(input.appellantId)}` : "";
  const settlementQuery = input.intentId ? `?intentIds=${encodeURIComponent(input.intentId)}` : "";
  const [claims, remittances, appeals, settlements] = await Promise.all([
    getRows(`/internal/v1/coverage/claims${claimsQuery ? `?${claimsQuery}` : ""}`),
    getRows("/internal/v1/coverage/remittances"),
    getRows(`/internal/v1/coverage/appeals${appealsQuery}`),
    input.intentId ? getRows(`/internal/v1/finance/settlements${settlementQuery}`) : Promise.resolve([]),
  ]);

  return {
    claims,
    remittances,
    appeals,
    settlements,
    reconciliation: [
      { stage: "Claims", status: claims.length > 0 ? "COMPLETE" : "PENDING", detail: `${claims.length} claim rows` },
      { stage: "Remittance", status: remittances.length > 0 ? "COMPLETE" : "PENDING", detail: `${remittances.length} remittance rows` },
      { stage: "Appeals", status: appeals.length > 0 ? "ACTION_REQUIRED" : "PENDING", detail: `${appeals.length} appeal rows` },
      { stage: "Settlements", status: settlements.length > 0 ? "COMPLETE" : "PENDING", detail: `${settlements.length} settlement rows` },
    ],
  };
}
