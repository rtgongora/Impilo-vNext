/**
 * Last-known citizen long-tail outcomes (Tier-3 wave 2 deepen) — small JSON in SecureStore.
 */

import * as SecureStore from "expo-secure-store";

const K = {
  shareIssue: "impilo.citizen.lt.share_issue",
  shareClaim: "impilo.citizen.lt.share_claim",
  credentialVerify: "impilo.citizen.lt.credential_verify",
  delegatedPickup: "impilo.citizen.lt.delegated_pickup",
} as const;

async function getJson<T>(key: string): Promise<T | null> {
  try {
    const raw = await SecureStore.getItemAsync(key);
    if (!raw) return null;
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

async function setJson(key: string, value: unknown): Promise<void> {
  await SecureStore.setItemAsync(key, JSON.stringify(value));
}

export interface CachedShareIssue {
  code: string;
  issued_at: string;
  purpose?: string | null;
}

export async function loadLastShareIssue(): Promise<CachedShareIssue | null> {
  return getJson<CachedShareIssue>(K.shareIssue);
}

export async function saveLastShareIssue(v: CachedShareIssue): Promise<void> {
  await setJson(K.shareIssue, v);
}

export interface CachedShareClaim {
  code: string;
  claimed_at: string;
}

export async function loadLastShareClaim(): Promise<CachedShareClaim | null> {
  return getJson<CachedShareClaim>(K.shareClaim);
}

export async function saveLastShareClaim(v: CachedShareClaim): Promise<void> {
  await setJson(K.shareClaim, v);
}

export interface CachedCredentialVerify {
  verified: boolean;
  checked_at: string;
}

export async function loadLastCredentialVerify(): Promise<CachedCredentialVerify | null> {
  return getJson<CachedCredentialVerify>(K.credentialVerify);
}

export async function saveLastCredentialVerify(v: CachedCredentialVerify): Promise<void> {
  await setJson(K.credentialVerify, v);
}

export interface CachedDelegatedPickup {
  token: string;
  issued_at: string;
  delegate_name: string;
  delegate_phone: string;
}

export async function loadLastDelegatedPickup(): Promise<CachedDelegatedPickup | null> {
  return getJson<CachedDelegatedPickup>(K.delegatedPickup);
}

export async function saveLastDelegatedPickup(v: CachedDelegatedPickup): Promise<void> {
  await setJson(K.delegatedPickup, v);
}
