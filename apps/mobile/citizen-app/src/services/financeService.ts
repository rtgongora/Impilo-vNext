/**
 * Finance Service — Citizen-facing balance, transactions, and pending charges.
 *
 * Audit gap: `G-3` (closed in Stage 3.4B). The previous implementation called
 *   `GET /internal/v1/mobile/citizen/finance/balance`
 *   `GET /internal/v1/mobile/citizen/finance/transactions`
 *   `GET /internal/v1/mobile/citizen/finance/charges?status=PENDING`
 * which **no controller in the platform implements** — every call was a
 * 404 at runtime (`docs/audits/costa-mushex-experience-layer-wiring-audit.md`,
 * §G-3, Stage 3.4A audit).
 *
 * Stage 3.4B re-targets the still-meaningful surfaces onto the canonical
 * Mushe Wallet plane (the only source of truth for citizen wallet balances
 * and transactions, per `docs/doctrine/mushex-gateway-neutrality.md`):
 *
 *   - {@link fetchBalance}      → `GET /internal/v1/wallet/me/balance`
 *   - {@link fetchTransactions} → `GET /internal/v1/wallet/me/transactions`
 *
 * The owner of the wallet is implied by the `x-actor-id` trust header that
 * the mobile API client injects from the Keycloak session — there is no
 * per-call ID parameter on the canonical plane.
 *
 * `fetchPendingCharges` is now wired to the live citizen COSTA route published by
 * experience-bff (`CitizenCostaController`):
 *   - {@link fetchPendingCharges} → `GET /internal/v1/mobile/citizen/costa/charges/pending`
 * composed from the sovereign COSTA outstanding-bills read. The owner (Health ID) is
 * implied by the `x-actor-id` trust header. Real outstanding-bill rows are adapted onto
 * `PendingCharge`; fields COSTA does not provide (service name, dates) are left empty
 * rather than fabricated. See `docs/implementation/mobile-costa-bff-contract.md`.
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { Transaction, Balance, PendingCharge } from "../types";

const WALLET = "/internal/v1/wallet";

/** BFF success envelope. Inner `data` shape varies by endpoint. */
type Envelope<T> = { data: T };

export async function fetchTransactions(_params: {
  page?: number;
  size?: number;
  from?: string;
  to?: string;
} = {}): Promise<Transaction[]> {
  // Pagination/date filters are not part of the canonical wallet read surface
  // yet. The parameter shape is preserved for source-compat with existing call
  // sites; we simply ignore the values until the upstream surface exposes
  // them.
  const response = await apiClient.get<Envelope<unknown>>(`${WALLET}/me/transactions`);
  return adaptTransactions(response.data?.data);
}

export async function fetchBalance(): Promise<Balance> {
  const response = await apiClient.get<Envelope<unknown>>(`${WALLET}/me/balance`);
  return adaptBalance(response.data?.data);
}

const CITIZEN_COSTA = "/internal/v1/mobile/citizen/costa";

export type PendingChargesResult = {
  charges: PendingCharge[];
  /** Retained for source-compat with call sites; the citizen COSTA route is now live. */
  blocked: boolean;
  blockedReason?: string;
};

/**
 * Live citizen pending charges — composed by experience-bff `CitizenCostaController`
 * from the sovereign COSTA outstanding-bills read. Adapts the real outstanding-bill list
 * onto {@link PendingCharge}; never fabricates rows.
 */
export async function fetchPendingCharges(): Promise<PendingChargesResult> {
  const response = await apiClient.get<Envelope<unknown>>(`${CITIZEN_COSTA}/charges/pending`);
  return { charges: adaptPendingCharges(response.data?.data), blocked: false };
}

// ── Adapters ────────────────────────────────────────────────────────────

/**
 * Maps the BFF balance payload onto the citizen UI `Balance` shape.
 *
 *   - Local fallback shape: `{ balance, availableBalance, currency, lastUpdated }`
 *   - Upstream Mushe shape:  may use the same names; `updatedAt` and `as_of`
 *     are accepted as defensive aliases.
 */
function adaptBalance(raw: unknown): Balance {
  const root = asRecord(raw) ?? {};
  // Some upstream shapes nest under `data` or `attributes`; unwrap defensively.
  const node = asRecord(root.data) ?? asRecord(root.attributes) ?? root;

  return {
    amount: pickNumber(node.balance, node.availableBalance, node.amount) ?? 0,
    currency: pickString(node.currency) ?? "USD",
    updatedAt:
      pickString(node.lastUpdated, node.updatedAt, node.as_of) ?? new Date().toISOString(),
  };
}

/**
 * Maps a wallet transactions payload onto the citizen UI `Transaction[]`
 * shape, tolerating both the flat array (BFF local fallback) and the Spring
 * Data Page shape (upstream Mushe).
 */
function adaptTransactions(raw: unknown): Transaction[] {
  const list = extractList(raw);
  return list.map(adaptTransaction);
}

function adaptTransaction(raw: unknown): Transaction {
  const root = asRecord(raw) ?? {};
  const attrs = asRecord(root.attributes) ?? root;
  const txnType = pickString(attrs.transactionType, attrs.txnType, attrs.type, attrs.method);

  return {
    id: pickString(root.id, attrs.id) ?? "",
    date:
      pickString(attrs.createdAt, attrs.created_at, attrs.date, attrs.timestamp) ??
      new Date(0).toISOString(),
    description: pickString(attrs.description, attrs.memo) ?? "",
    amount: pickNumber(attrs.amount) ?? 0,
    currency: pickString(attrs.currency) ?? "USD",
    type: txnType === "CREDIT" ? "CREDIT" : "DEBIT",
    status: pickString(attrs.status) ?? "COMPLETED",
    reference: pickString(attrs.reference, attrs.externalRef),
    category: pickString(attrs.category),
  };
}

/**
 * Maps COSTA `OutstandingBillDto` rows ({ billId, facilityId, encounterId, patientPayable,
 * totalPaidOnBill, balanceDue, currency }) onto the citizen UI `PendingCharge` shape.
 * Only genuinely-present fields are mapped — no service names or charge dates are invented.
 */
function adaptPendingCharges(raw: unknown): PendingCharge[] {
  return extractList(raw).map((row) => {
    const r = asRecord(row) ?? {};
    const encounterId = pickString(r.encounterId);
    return {
      id: pickString(r.billId, r.id) ?? "",
      description: encounterId ? `Care episode ${encounterId}` : "Outstanding bill",
      amount: pickNumber(r.balanceDue, r.patientPayable, r.amount) ?? 0,
      currency: pickString(r.currency) ?? "USD",
      chargeDate: "",
      status: "PENDING",
    };
  });
}

// ── Tiny helpers (intentionally local to keep this file self-contained) ──

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
