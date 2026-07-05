/**
 * Experience UI — Dura (sovereign stock brain) via BFF.
 *
 * Proxies to `/internal/v1/dura/**` (BFF `DuraBffController`), which forwards to
 * inventory-service `/v1/dura/**`. Responses use the shared ApiResponse envelope
 * `{ success, data, ... }`; hooks unwrap `data`.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

interface Envelope<T> {
  success: boolean;
  data: T;
  correlationId?: string;
}

function q(params: Record<string, string | number | boolean | undefined | null>): string {
  const usp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === "") continue;
    usp.set(k, String(v));
  }
  const s = usp.toString();
  return s ? `?${s}` : "";
}

// ── Types ─────────────────────────────────────────────────────────

export interface DuraCategory {
  categoryId: string;
  code: string;
  name: string;
  programmeArea?: string | null;
}

export interface DuraCommodity {
  itemId: string;
  itemCode: string;
  name: string;
  genericName?: string | null;
  programmeArea?: string | null;
  controlled: boolean;
  coldChainRequired: boolean;
}

export interface DuraBatch {
  batchLotId: string;
  itemCode: string;
  batchNumber: string;
  expiryDate?: string | null;
  status: string;
}

export interface DuraRecall {
  recallId: string;
  itemCode?: string | null;
  batchNumber?: string | null;
  severity: string;
  status: string;
  affectedBatches: number;
  reason?: string | null;
}

export interface DuraExcursion {
  excursionId: string;
  ccLocationId: string;
  temperature: number;
  breach: string;
  status: string;
}

/** Materialised on-hand projection row (inventory-service `OnHandDto`). */
export interface DuraOnHandRow {
  id: string;
  facilityId: string;
  storeId: string;
  binId?: string | null;
  itemCode: string;
  batch?: string | null;
  expiry?: string | null;
  qtyOnHand: number;
  updatedAt?: string | null;
}

/** Stockout rows share the on-hand projection shape (qtyOnHand ≤ 0). */
export type DuraStockoutRow = DuraOnHandRow;

interface Paged<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface DuraLedgerEvent {
  eventId: string;
  facilityId: string;
  storeId: string;
  binId?: string | null;
  itemCode: string;
  batch?: string | null;
  expiry?: string | null;
  qtyDelta: number;
  uom?: string | null;
  eventType: string;
  reason?: string | null;
  refType?: string | null;
  refId?: string | null;
  actorId?: string | null;
  createdAt?: string | null;
}

export interface DuraExternalSyncState {
  syncId: string;
  externalSystem: "ELMIS" | "NATPHARM" | string;
  entityType: string;
  entityRef: string;
  externalRef?: string | null;
  status: "PENDING" | "SYNCED" | "FAILED" | "RETRY" | string;
  attempts: number;
  lastError?: string | null;
  createdAt?: string | null;
  lastAttemptAt?: string | null;
  syncedAt?: string | null;
}

// ── Hooks ─────────────────────────────────────────────────────────

export function useDuraCategories(programmeArea?: string) {
  return useQuery({
    queryKey: ["dura-categories", programmeArea],
    queryFn: () =>
      apiClient
        .get<Envelope<DuraCategory[]>>(`/internal/v1/dura/categories${q({ programmeArea })}`)
        .then((r) => r.data ?? []),
  });
}

export function useDuraCommodities(opts?: {
  q?: string;
  programmeArea?: string;
  controlled?: boolean;
  coldChain?: boolean;
}) {
  return useQuery({
    queryKey: ["dura-commodities", opts],
    queryFn: () =>
      apiClient
        .get<Envelope<DuraCommodity[]>>(
          `/internal/v1/dura/commodities${q({
            q: opts?.q,
            programmeArea: opts?.programmeArea,
            controlled: opts?.controlled,
            coldChain: opts?.coldChain,
          })}`,
        )
        .then((r) => r.data ?? []),
  });
}

export function useDuraNearExpiryBatches(days = 90) {
  return useQuery({
    queryKey: ["dura-near-expiry", days],
    queryFn: () =>
      apiClient
        .get<Envelope<DuraBatch[]>>(`/internal/v1/dura/batches/near-expiry${q({ days })}`)
        .then((r) => r.data ?? []),
  });
}

export function useDuraRecalls(status?: string) {
  return useQuery({
    queryKey: ["dura-recalls", status],
    queryFn: () =>
      apiClient
        .get<Envelope<DuraRecall[]>>(`/internal/v1/dura/recalls${q({ status })}`)
        .then((r) => r.data ?? []),
  });
}

export function useDuraColdChainExcursions(status?: string) {
  return useQuery({
    queryKey: ["dura-cold-chain-excursions", status],
    queryFn: () =>
      apiClient
        .get<Envelope<DuraExcursion[]>>(`/internal/v1/dura/cold-chain/excursions${q({ status })}`)
        .then((r) => r.data ?? []),
  });
}

/**
 * Stockouts (on-hand ≤ 0) for a facility. Backed by
 * `/internal/v1/inventory/on-hand/stockouts` → inventory-service
 * `/v1/onhand/stockouts` (materialised on-hand projection derived from the
 * append-only ledger). Disabled until a facility UUID is available.
 */
export function useDuraStockouts(facilityId?: string) {
  return useQuery({
    queryKey: ["dura-stockouts", facilityId],
    enabled: Boolean(facilityId),
    queryFn: () =>
      apiClient
        .get<Envelope<DuraStockoutRow[]>>(
          `/internal/v1/inventory/on-hand/stockouts${q({ facilityId })}`,
        )
        .then((r) => r.data ?? []),
  });
}

/**
 * Facility stock balance (ledger-derived on-hand projection, paginated).
 * Backed by `/internal/v1/inventory/on-hand` → inventory-service `/v1/onhand`.
 */
export function useDuraOnHand(facilityId?: string, opts?: { itemCode?: string; size?: number }) {
  return useQuery({
    queryKey: ["dura-on-hand", facilityId, opts],
    enabled: Boolean(facilityId),
    queryFn: () =>
      apiClient
        .get<Envelope<Paged<DuraOnHandRow>>>(
          `/internal/v1/inventory/on-hand${q({
            facilityId,
            itemCode: opts?.itemCode,
            size: opts?.size ?? 20,
          })}`,
        )
        .then((r) => r.data?.items ?? []),
  });
}

/**
 * Append-only stock ledger events (movement history, newest page first from
 * the service default sort). Backed by `/internal/v1/inventory/ledger` →
 * inventory-service `/v1/ledger`. Every balance shown in the UI derives from
 * these movements — this is the audit surface for stock truth.
 */
export function useDuraLedger(facilityId?: string, opts?: { itemCode?: string; size?: number }) {
  return useQuery({
    queryKey: ["dura-ledger", facilityId, opts],
    enabled: Boolean(facilityId),
    queryFn: () =>
      apiClient
        .get<Envelope<Paged<DuraLedgerEvent>>>(
          `/internal/v1/inventory/ledger${q({
            facilityId,
            itemCode: opts?.itemCode,
            size: opts?.size ?? 20,
          })}`,
        )
        .then((r) => r.data?.items ?? []),
  });
}

/**
 * eLMIS/NatPharm external sync states from the real Dura sync state machine
 * (`/internal/v1/dura/external-sync`). An empty list means no sync activity —
 * the pharmacy-eLMIS dispense sync adapter is a recorded deferred seam, so
 * absence of activity is the honest state, not an error.
 */
export function useDuraExternalSync(status?: string) {
  return useQuery({
    queryKey: ["dura-external-sync", status],
    queryFn: () =>
      apiClient
        .get<Envelope<DuraExternalSyncState[]>>(`/internal/v1/dura/external-sync${q({ status })}`)
        .then((r) => r.data ?? []),
  });
}
