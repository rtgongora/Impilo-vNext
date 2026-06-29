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
