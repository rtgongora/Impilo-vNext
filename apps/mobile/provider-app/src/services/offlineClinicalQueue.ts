import { ApiError, isRetryable, type NetworkError, type TimeoutError } from "@impilo/mobile-api-client";
import { getOfflineStorage } from "@impilo/mobile-offline";
import { generateId } from "@impilo/mobile-trust";
import type { OfflineRecord, QueuedOperation } from "@impilo/mobile-offline";

export interface QueueClinicalCreateInput {
  collection: string;
  path: string;
  payload: Record<string, unknown>;
  recordId?: string;
  method?: "POST" | "PUT" | "PATCH";
  operationType?: "CREATE" | "UPDATE";
}

export interface QueueClinicalCreateResult {
  recordId: string;
  queued: boolean;
}

function shouldQueueOffline(error: unknown): error is ApiError | NetworkError | TimeoutError {
  return isRetryable(error);
}

export async function queueClinicalCreate(input: QueueClinicalCreateInput): Promise<QueueClinicalCreateResult> {
  const storage = getOfflineStorage();
  const now = new Date().toISOString();
  const recordId = input.recordId ?? `local-${generateId()}`;

  const existing = await storage.get<Record<string, unknown>>(input.collection, recordId);
  const record: OfflineRecord<Record<string, unknown>> = {
    id: recordId,
    collection: input.collection,
    data: input.payload,
    version: (existing?.version ?? 0) + 1,
    localVersion: (existing?.localVersion ?? 0) + 1,
    serverVersion: existing?.serverVersion ?? 0,
    status: "pending",
    createdAt: existing?.createdAt ?? now,
    updatedAt: now,
  };

  await storage.put(record);

  const operation: QueuedOperation = {
    id: generateId(),
    type: input.operationType ?? "CREATE",
    collection: input.collection,
    recordId,
    payload: input.payload,
    method: input.method ?? "POST",
    path: input.path,
    createdAt: now,
    retryCount: 0,
    maxRetries: 5,
    status: "pending",
    idempotencyKey: generateId(),
  };
  await storage.enqueue(operation);

  return { recordId, queued: true };
}

export async function queueClinicalCreateOnRetryableError(
  error: unknown,
  input: QueueClinicalCreateInput
): Promise<QueueClinicalCreateResult | null> {
  if (!shouldQueueOffline(error)) {
    return null;
  }

  try {
    return await queueClinicalCreate(input);
  } catch {
    return null;
  }
}
