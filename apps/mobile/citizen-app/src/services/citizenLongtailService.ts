/**
 * Citizen long-tail API — Tier-3 wave 2 BFF endpoints (`CitizenLongtailController`).
 */

import { generateId } from "@impilo/mobile-trust";
import { apiClient } from "@impilo/mobile-api-client";
import { getOfflineStorage } from "@impilo/mobile-offline";
import type { QueuedOperation } from "@impilo/mobile-offline";

const BASE = "/internal/v1/mobile/citizen";

type Envelope<D> = { data: D; meta?: Record<string, unknown> };

export interface IssueShareCodeResult {
  code: string;
  issued_at: string;
  purpose?: string | null;
}

export async function issueShareCode(purpose?: string | null): Promise<IssueShareCodeResult> {
  const response = await apiClient.post<Envelope<IssueShareCodeResult>>(`${BASE}/share/issue`, {
    purpose: purpose ?? null,
  });
  return response.data.data;
}

export interface ClaimShareResult {
  claimed: boolean;
  code: string | null;
  claimed_at: string;
}

export async function claimSharedDocuments(code: string): Promise<ClaimShareResult> {
  const response = await apiClient.post<Envelope<ClaimShareResult>>(`${BASE}/share/claim`, { code });
  return response.data.data;
}

export interface VerifyCredentialResult {
  verified: boolean;
  checked_at: string;
}

export async function verifyCredential(payload: string): Promise<VerifyCredentialResult> {
  const response = await apiClient.post<Envelope<VerifyCredentialResult>>(`${BASE}/credential/verify`, {
    payload,
  });
  return response.data.data;
}

export interface DelegatedPickupResult {
  token: string;
  delegate_name: string | null;
  delegate_phone: string | null;
  issued_at: string;
}

export async function issueDelegatedPickup(delegateName: string, delegatePhone: string): Promise<DelegatedPickupResult> {
  const response = await apiClient.post<Envelope<DelegatedPickupResult>>(`${BASE}/delegated-pickup/issue`, {
    delegate_name: delegateName,
    delegate_phone: delegatePhone,
  });
  return response.data.data;
}

/**
 * Queue a POST for background sync when the device is offline (sync engine processes on reconnect).
 */
export async function enqueueCitizenLongtailPost(path: string, payload: unknown, collection: string): Promise<void> {
  const storage = getOfflineStorage();
  const recordId = generateId();
  const now = new Date().toISOString();
  const op: QueuedOperation = {
    id: generateId(),
    type: "CREATE",
    collection,
    recordId,
    payload,
    method: "POST",
    path,
    createdAt: now,
    retryCount: 0,
    maxRetries: 5,
    status: "pending",
    idempotencyKey: generateId(),
  };
  await storage.enqueue(op);
}
