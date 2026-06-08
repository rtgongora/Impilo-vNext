/**
 * MusheX platform administration — thin TanStack Query hooks (read-only).
 *
 * <p>Used by the canonical MusheX platform admin hub page
 * ({@code /finance/mushex-platform}). The upstream is reached via Experience
 * BFF {@code /internal/v1/finance/mushex-platform/**}, which proxies the
 * MusheX service's custodial wallet, remittance, card profile, and reversal
 * admin APIs (`FinanceMushexPlatformController`). All requests pick up v1.2
 * trust headers via {@link apiClient}; no new API client is introduced.</p>
 *
 * <p>Stage 3.3 ({@code G-2}) — the hub primarily surfaces GET routes. Wave 4 adds
 * one controlled write mutation (wallet credit / top-up) where the BFF already
 * proxies {@code POST /wallets/{walletId}/credit}.</p>
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

/**
 * Upstream MusheX platform responses are opaque JSON forwarded through the
 * BFF proxy. The hub UI only needs to derive a count and a "did we get a
 * useful response" flag, so we keep the type intentionally permissive.
 */
export type MushexPlatformPayload = unknown;

/**
 * Derive a row count from any of the common envelope shapes the MusheX
 * service is known to return:
 *
 * - a top-level array;
 * - {@code { data: [...] }};
 * - {@code { items: [...] }};
 * - {@code { content: [...] }} (Spring page);
 * - an envelope with a {@code totalElements} / {@code total_elements} /
 *   {@code total} numeric field.
 *
 * Anything else returns {@code null} so the page can render an honest
 * "shape not recognised" state rather than fabricating a count.
 */
export function extractCount(payload: MushexPlatformPayload): number | null {
  if (payload == null) return null;
  if (Array.isArray(payload)) return payload.length;

  if (typeof payload === "object") {
    const obj = payload as Record<string, unknown>;

    if (Array.isArray(obj.data)) return (obj.data as unknown[]).length;
    if (Array.isArray(obj.items)) return (obj.items as unknown[]).length;
    if (Array.isArray(obj.content)) return (obj.content as unknown[]).length;

    const totals = ["totalElements", "total_elements", "total"] as const;
    for (const key of totals) {
      const value = obj[key];
      if (typeof value === "number" && Number.isFinite(value)) {
        return value;
      }
    }
  }

  return null;
}

export function useMushexPlatformWallets() {
  return useQuery<MushexPlatformPayload>({
    queryKey: ["finance", "mushex-platform", "wallets"],
    queryFn: () =>
      apiClient.get<MushexPlatformPayload>(
        "/internal/v1/finance/mushex-platform/wallets",
      ),
  });
}

export function useMushexPlatformRemittanceTransfers() {
  return useQuery<MushexPlatformPayload>({
    queryKey: ["finance", "mushex-platform", "remittance-transfers"],
    queryFn: () =>
      apiClient.get<MushexPlatformPayload>(
        "/internal/v1/finance/mushex-platform/remittance-transfers",
      ),
  });
}

export function useMushexPlatformCardProfiles() {
  return useQuery<MushexPlatformPayload>({
    queryKey: ["finance", "mushex-platform", "card-profiles"],
    queryFn: () =>
      apiClient.get<MushexPlatformPayload>(
        "/internal/v1/finance/mushex-platform/card-profiles",
      ),
  });
}

export function useMushexPlatformReversals() {
  return useQuery<MushexPlatformPayload>({
    queryKey: ["finance", "mushex-platform", "reversals"],
    queryFn: () =>
      apiClient.get<MushexPlatformPayload>(
        "/internal/v1/finance/mushex-platform/reversals",
      ),
  });
}

/**
 * Phase 2 (real payment rail readiness) — read-only adapter readiness snapshot.
 * Surfaces per-rail readiness derived from MusheX configuration + adapter
 * registry presence. The endpoint never returns credentials and never causes
 * money movement; see {@code services/mushex-service/.../AdapterReadinessService}.
 */
export interface AdapterReadinessRow {
  adapterType: "MOBILE_MONEY" | "BANK_TRANSFER" | "CARD_GATEWAY" | "SANDBOX";
  status:
    | "NOT_REGISTERED"
    | "DISABLED"
    | "READY_SANDBOX"
    | "CREDENTIALS_MISSING"
    | "READY_LIVE"
    | "DEGRADED";
  liveCapable: boolean;
  sandboxCapable: boolean;
  detail: string;
}

interface AdapterReadinessEnvelope {
  data?: AdapterReadinessRow[];
}

export function useMushexPlatformAdapterReadiness() {
  return useQuery<AdapterReadinessRow[]>({
    queryKey: ["finance", "mushex-platform", "adapter-readiness"],
    queryFn: async () => {
      const raw = await apiClient.get<AdapterReadinessEnvelope | AdapterReadinessRow[]>(
        "/internal/v1/finance/mushex-platform/adapter-readiness",
      );
      if (Array.isArray(raw)) return raw;
      if (raw && Array.isArray(raw.data)) return raw.data;
      return [];
    },
  });
}

/**
 * Phase 4 (MusheX platform read-to-detail) — per-wallet transactions drill-down
 * over the existing {@code GET /mushex/v1/platform/wallets/{walletId}/transactions}
 * endpoint (BFF passthrough already wired). Read-only; no write actions.
 */
export function useMushexPlatformWalletTransactions(walletId: string | null | undefined) {
  return useQuery<MushexPlatformPayload>({
    queryKey: ["finance", "mushex-platform", "wallets", walletId, "transactions"],
    queryFn: () =>
      apiClient.get<MushexPlatformPayload>(
        `/internal/v1/finance/mushex-platform/wallets/${encodeURIComponent(walletId ?? "")}/transactions`,
      ),
    enabled: typeof walletId === "string" && walletId.length > 0,
  });
}

/**
 * Phase 4 — card profiles linked to a single wallet, via the existing
 * {@code GET /mushex/v1/platform/card-profiles?walletId=...} filter
 * supported by the upstream CardProfilePlatformController. Read-only.
 */
export function useMushexPlatformCardProfilesForWallet(walletId: string | null | undefined) {
  return useQuery<MushexPlatformPayload>({
    queryKey: ["finance", "mushex-platform", "card-profiles", { walletId }],
    queryFn: () =>
      apiClient.get<MushexPlatformPayload>(
        `/internal/v1/finance/mushex-platform/card-profiles?walletId=${encodeURIComponent(walletId ?? "")}`,
      ),
    enabled: typeof walletId === "string" && walletId.length > 0,
  });
}

/**
 * Phase 4 follow-on — per-wallet identity read over
 * {@code GET /internal/v1/finance/mushex-platform/wallets/{walletId}}.
 */
export function useMushexPlatformWalletById(walletId: string | null | undefined) {
  return useQuery<MushexPlatformPayload>({
    queryKey: ["finance", "mushex-platform", "wallet", walletId],
    queryFn: () =>
      apiClient.get<MushexPlatformPayload>(
        `/internal/v1/finance/mushex-platform/wallets/${encodeURIComponent(walletId ?? "")}`,
      ),
    enabled: typeof walletId === "string" && walletId.length > 0,
  });
}

/**
 * Phase 4 follow-on — per-remittance detail over
 * {@code GET /internal/v1/finance/mushex-platform/remittance-transfers/{transferId}}.
 */
export function useMushexPlatformRemittanceTransferById(transferId: string | null | undefined) {
  return useQuery<MushexPlatformPayload>({
    queryKey: ["finance", "mushex-platform", "remittance-transfer", transferId],
    queryFn: () =>
      apiClient.get<MushexPlatformPayload>(
        `/internal/v1/finance/mushex-platform/remittance-transfers/${encodeURIComponent(transferId ?? "")}`,
      ),
    enabled: typeof transferId === "string" && transferId.length > 0,
  });
}

/**
 * Phase 4 follow-on — per-card detail over
 * {@code GET /internal/v1/finance/mushex-platform/card-profiles/{cardId}}.
 */
export function useMushexPlatformCardProfileById(cardId: string | null | undefined) {
  return useQuery<MushexPlatformPayload>({
    queryKey: ["finance", "mushex-platform", "card-profile", cardId],
    queryFn: () =>
      apiClient.get<MushexPlatformPayload>(
        `/internal/v1/finance/mushex-platform/card-profiles/${encodeURIComponent(cardId ?? "")}`,
      ),
    enabled: typeof cardId === "string" && cardId.length > 0,
  });
}

/**
 * Phase 4 follow-on — per-reversal detail over
 * {@code GET /internal/v1/finance/mushex-platform/reversals/{reversalId}}.
 */
export function useMushexPlatformReversalById(reversalId: string | null | undefined) {
  return useQuery<MushexPlatformPayload>({
    queryKey: ["finance", "mushex-platform", "reversal", reversalId],
    queryFn: () =>
      apiClient.get<MushexPlatformPayload>(
        `/internal/v1/finance/mushex-platform/reversals/${encodeURIComponent(reversalId ?? "")}`,
      ),
    enabled: typeof reversalId === "string" && reversalId.length > 0,
  });
}

/**
 * Coerce the various envelope shapes the MusheX platform API may return
 * (`[]`, `{ data: [] }`, `{ items: [] }`, `{ content: [] }`) into a plain
 * array of records. Anything else returns an empty array so the UI surfaces
 * an honest "no rows" / unrecognised-envelope state without fabricating data.
 */
export function extractRows(payload: MushexPlatformPayload): Record<string, unknown>[] {
  if (payload == null) return [];
  if (Array.isArray(payload)) return payload as Record<string, unknown>[];
  if (typeof payload === "object") {
    const obj = payload as Record<string, unknown>;
    if (Array.isArray(obj.data)) return obj.data as Record<string, unknown>[];
    if (Array.isArray(obj.items)) return obj.items as Record<string, unknown>[];
    if (Array.isArray(obj.content)) return obj.content as Record<string, unknown>[];
  }
  return [];
}

/**
 * Coerce a single-record envelope into an object. Accepts either
 * a top-level object or {@code { data: { ... } }}.
 */
export function extractRecord(payload: MushexPlatformPayload): Record<string, unknown> | null {
  if (payload == null) return null;
  if (typeof payload !== "object" || Array.isArray(payload)) return null;
  const obj = payload as Record<string, unknown>;
  if (obj.data && typeof obj.data === "object" && !Array.isArray(obj.data)) {
    return obj.data as Record<string, unknown>;
  }
  return obj;
}

/**
 * Wave 4 controlled write — custodial wallet credit (top-up).
 * POST /internal/v1/finance/mushex-platform/wallets/{walletId}/credit
 */
export function useMushexPlatformWalletCredit(walletId: string | null | undefined) {
  const qc = useQueryClient();
  return useMutation<MushexPlatformPayload, unknown, Record<string, unknown>>({
    mutationFn: (body) =>
      apiClient.post<MushexPlatformPayload>(
        `/internal/v1/finance/mushex-platform/wallets/${encodeURIComponent(walletId ?? "")}/credit`,
        body,
      ),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["finance", "mushex-platform", "wallets"] });
      if (walletId) {
        await qc.invalidateQueries({
          queryKey: ["finance", "mushex-platform", "wallets", walletId, "transactions"],
        });
        await qc.invalidateQueries({
          queryKey: ["finance", "mushex-platform", "wallet", walletId],
        });
      }
    },
  });
}

/**
 * Wave 4 controlled write — submit remittance transfer.
 * POST /internal/v1/finance/mushex-platform/remittance-transfers
 */
export function useMushexPlatformCreateRemittance() {
  const qc = useQueryClient();
  return useMutation<MushexPlatformPayload, unknown, Record<string, unknown>>({
    mutationFn: (body) =>
      apiClient.post<MushexPlatformPayload>(
        "/internal/v1/finance/mushex-platform/remittance-transfers",
        body,
      ),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["finance", "mushex-platform", "remittance-transfers"] });
    },
  });
}
