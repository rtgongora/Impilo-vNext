/**
 * Health Wallet Service — Citizen-facing balance and transaction reads.
 *
 * Routes are the canonical Mushe Wallet plane proxied by experience-bff
 * (`WalletController` → `MusheWalletServiceClient` → mushe-wallet-service):
 *
 *   GET /internal/v1/wallet/me              → owner-scoped wallet
 *   GET /internal/v1/wallet/me/transactions → owner-scoped transactions
 *
 * Doctrine: `docs/doctrine/mushex-gateway-neutrality.md` ("wallet routes must
 * resolve through Mushe Wallet"). Audit gap: `G-3` (mobile citizen wallet was
 * previously calling the wellness-service proxy `/internal/v1/mobile/citizen/wallet`).
 *
 * The owner of the wallet is implied by the `x-actor-id` trust header that the
 * mobile API client injects from the Keycloak session — there is no
 * `patientId` query parameter on the canonical plane.
 *
 * Both endpoints can return either:
 *   - the upstream Mushe payload pass-through, or
 *   - the BFF's `allow-local-fallback=true` in-memory shape, or
 *   - a 503 `{ error: { code: "WALLET_UPSTREAM_UNAVAILABLE" } }` when fallback
 *     is disabled and upstream is down (surfaced to UI as an `ApiError`).
 *
 * The adapters in this module tolerate every documented success shape so the
 * UI keeps using the existing `HealthWallet` and `WalletTransaction` types.
 */
import { apiClient } from "@impilo/mobile-api-client";
import type { HealthWallet, WalletTransaction } from "../types";

const V1 = "/internal/v1/wallet";

/** BFF success envelope. The inner `data` shape varies by endpoint. */
type Envelope<T> = { data: T };

/** Owner-scoped wallet (`GET /me`). */
export async function fetchWallet(): Promise<HealthWallet> {
  const response = await apiClient.get<Envelope<unknown>>(`${V1}/me`);
  return adaptWallet(response.data?.data);
}

/** Owner-scoped wallet transactions (`GET /me/transactions`). */
export async function fetchTransactions(): Promise<WalletTransaction[]> {
  const response = await apiClient.get<Envelope<unknown>>(`${V1}/me/transactions`);
  return adaptTransactions(response.data?.data);
}

// ── Adapters ────────────────────────────────────────────────────────────

/**
 * Normalises a wallet payload from any of the three documented shapes:
 *
 *   - Upstream Mushe flat:    `{ id, currency, balance, availableBalance, status }`
 *   - BFF local fallback:     `{ id, type, attributes: { id, currency, balance, ... } }`
 *   - JSON:API-style:         `{ data: { id, type, attributes: {...} } }` (defensive)
 *
 * Always returns the four fields the citizen `HealthWallet` UI relies on.
 */
function adaptWallet(raw: unknown): HealthWallet {
  const root = asRecord(raw) ?? {};
  // Unwrap one extra JSON:API `data` layer if present.
  const node = asRecord(root.data) ?? root;
  const attrs = asRecord(node.attributes) ?? node;

  return {
    id: pickString(node.id, attrs.id) ?? "",
    balance: pickNumber(attrs.balance) ?? 0,
    currency: pickString(attrs.currency) ?? "USD",
    status: pickString(attrs.status) ?? "ACTIVE",
  };
}

/**
 * Normalises a transactions payload into a flat array, tolerating:
 *
 *   - Plain array:           `[ {...}, {...} ]` (BFF local fallback)
 *   - Spring Data Page:      `{ content: [...], page: {...} }` (upstream Mushe)
 *   - Generic items wrapper: `{ items: [...] }` (defensive)
 *
 * Each row is then mapped onto the citizen `WalletTransaction` type, accepting
 * the legacy snake_case fields too so historical responses still render.
 */
function adaptTransactions(raw: unknown): WalletTransaction[] {
  const list = extractList(raw);
  return list.map(adaptTransaction);
}

function adaptTransaction(raw: unknown): WalletTransaction {
  const root = asRecord(raw) ?? {};
  const attrs = asRecord(root.attributes) ?? root;

  return {
    id: pickString(root.id, attrs.id) ?? "",
    transactionType:
      pickString(attrs.transactionType, attrs.txnType, attrs.type, attrs.method) ?? "DEBIT",
    amount: pickNumber(attrs.amount) ?? 0,
    currency: pickString(attrs.currency) ?? "USD",
    description: pickString(attrs.description, attrs.memo),
    reference: pickString(attrs.reference, attrs.externalRef),
    status: pickString(attrs.status) ?? "COMPLETED",
    createdAt:
      pickString(attrs.createdAt, attrs.created_at, attrs.timestamp) ?? new Date(0).toISOString(),
  };
}

// ── Tiny helpers (intentionally local) ──────────────────────────────────

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : undefined;
}

function extractList(raw: unknown): unknown[] {
  if (Array.isArray(raw)) return raw;
  const root = asRecord(raw);
  if (!root) return [];
  if (Array.isArray(root.content)) return root.content;
  if (Array.isArray(root.items)) return root.items;
  if (Array.isArray(root.data)) return root.data;
  return [];
}

function pickString(...candidates: unknown[]): string | undefined {
  for (const c of candidates) {
    if (typeof c === "string" && c.length > 0) return c;
  }
  return undefined;
}

function pickNumber(...candidates: unknown[]): number | undefined {
  for (const c of candidates) {
    if (typeof c === "number" && Number.isFinite(c)) return c;
    if (typeof c === "string" && c.length > 0) {
      const n = Number(c);
      if (Number.isFinite(n)) return n;
    }
  }
  return undefined;
}
