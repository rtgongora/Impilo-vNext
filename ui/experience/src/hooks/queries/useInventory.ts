/**
 * Experience UI — Inventory & supply chain (inventory-service via BFF).
 *
 * Proxies to `/internal/v1/inventory/**` (BFF `InventorySupplyBffController`).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

function q(params: Record<string, string | number | undefined | null>): string {
  const usp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === "") continue;
    usp.set(k, String(v));
  }
  const s = usp.toString();
  return s ? `?${s}` : "";
}

// ── Stock (on-hand) ───────────────────────────────────────────────

export function useInventoryOnHand(
  facilityId?: string | null,
  opts?: { storeId?: string; binId?: string; itemCode?: string; page?: number; size?: number },
) {
  return useQuery({
    queryKey: ["inventory-on-hand", facilityId, opts],
    queryFn: () =>
      apiClient.get<unknown>(
        `/internal/v1/inventory/on-hand${q({
          facilityId,
          storeId: opts?.storeId,
          binId: opts?.binId,
          itemCode: opts?.itemCode,
          page: opts?.page ?? 0,
          size: opts?.size ?? 20,
        })}`,
      ),
    enabled: !!facilityId,
  });
}

export function useInventoryNearExpiry(facilityId?: string | null, days = 30) {
  return useQuery({
    queryKey: ["inventory-near-expiry", facilityId, days],
    queryFn: () =>
      apiClient.get<unknown>(
        `/internal/v1/inventory/on-hand/near-expiry${q({ facilityId, days })}`,
      ),
    enabled: !!facilityId,
  });
}

export function useInventoryStockouts(facilityId?: string | null) {
  return useQuery({
    queryKey: ["inventory-stockouts", facilityId],
    queryFn: () =>
      apiClient.get<unknown>(`/internal/v1/inventory/on-hand/stockouts${q({ facilityId })}`),
    enabled: !!facilityId,
  });
}

// ── Ledger (movements) ─────────────────────────────────────────────

export function useInventoryLedger(
  facilityId?: string | null,
  opts?: { storeId?: string; itemCode?: string; page?: number; size?: number },
) {
  return useQuery({
    queryKey: ["inventory-ledger", facilityId, opts],
    queryFn: () =>
      apiClient.get<unknown>(
        `/internal/v1/inventory/ledger${q({
          facilityId,
          storeId: opts?.storeId,
          itemCode: opts?.itemCode,
          page: opts?.page ?? 0,
          size: opts?.size ?? 20,
        })}`,
      ),
    enabled: !!facilityId,
  });
}

// ── Reconciliation ───────────────────────────────────────────────

export function useInventoryReconcilePending(page = 0, size = 20) {
  return useQuery({
    queryKey: ["inventory-reconcile-pending", page, size],
    queryFn: () =>
      apiClient.get<unknown>(`/internal/v1/inventory/reconcile/pending${q({ page, size })}`),
  });
}

// ── Items ───────────────────────────────────────────────────────

export function useInventoryItem(itemCode?: string | null) {
  return useQuery({
    queryKey: ["inventory-item", itemCode],
    queryFn: () => apiClient.get<unknown>(`/internal/v1/inventory/items/${encodeURIComponent(itemCode!)}`),
    enabled: !!itemCode,
  });
}

export function useInventoryBarcodeLookup(code?: string | null) {
  return useQuery({
    queryKey: ["inventory-barcode", code],
    queryFn: () =>
      apiClient.get<unknown>(`/internal/v1/inventory/items/lookup-by-barcode${q({ code })}`),
    enabled: !!code,
  });
}

// ── Count sessions ────────────────────────────────────────────────

export function useInventoryCountSession(sessionId?: string | null) {
  return useQuery({
    queryKey: ["inventory-count-session", sessionId],
    queryFn: () =>
      apiClient.get<unknown>(`/internal/v1/inventory/counts/${encodeURIComponent(sessionId!)}`),
    enabled: !!sessionId,
  });
}

// ── Mutations — items & ledger ────────────────────────────────────

export function useInventoryCreateItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<unknown>("/internal/v1/inventory/items", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["inventory-item"] });
      qc.invalidateQueries({ queryKey: ["inventory-barcode"] });
    },
  });
}

export function useInventoryLedgerReceipt() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<unknown>("/internal/v1/inventory/ledger/receipt", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["inventory-ledger"] });
      qc.invalidateQueries({ queryKey: ["inventory-on-hand"] });
    },
  });
}

export function useInventoryLedgerTransfer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<unknown>("/internal/v1/inventory/ledger/transfer", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["inventory-ledger"] });
      qc.invalidateQueries({ queryKey: ["inventory-on-hand"] });
    },
  });
}

export function useInventoryLedgerIssue() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<unknown>("/internal/v1/inventory/ledger/issue", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["inventory-ledger"] });
      qc.invalidateQueries({ queryKey: ["inventory-on-hand"] });
    },
  });
}

export function useInventoryLedgerAdjust() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<unknown>("/internal/v1/inventory/ledger/adjust", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["inventory-ledger"] });
      qc.invalidateQueries({ queryKey: ["inventory-on-hand"] });
    },
  });
}

// ── Mutations — counts ───────────────────────────────────────────

export function useInventoryCreateCountSession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<unknown>("/internal/v1/inventory/counts", body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["inventory-count-session"] }),
  });
}

export function useInventoryStartCount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: string) =>
      apiClient.post<unknown>(`/internal/v1/inventory/counts/${encodeURIComponent(sessionId)}/start`, {}),
    onSuccess: (_d, sessionId) => {
      qc.invalidateQueries({ queryKey: ["inventory-count-session", sessionId] });
    },
  });
}

export function useInventoryUpdateCountLine() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { sessionId: string; lineId: string; body: Record<string, unknown> }) =>
      apiClient.post<unknown>(
        `/internal/v1/inventory/counts/${encodeURIComponent(args.sessionId)}/lines/${encodeURIComponent(args.lineId)}`,
        args.body,
      ),
    onSuccess: (_d, { sessionId }) => {
      qc.invalidateQueries({ queryKey: ["inventory-count-session", sessionId] });
    },
  });
}

export function useInventorySubmitCount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: string) =>
      apiClient.post<unknown>(`/internal/v1/inventory/counts/${encodeURIComponent(sessionId)}/submit`, {}),
    onSuccess: (_d, sessionId) => {
      qc.invalidateQueries({ queryKey: ["inventory-count-session", sessionId] });
      qc.invalidateQueries({ queryKey: ["inventory-on-hand"] });
    },
  });
}

export function useInventoryApproveCount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { sessionId: string; body?: Record<string, unknown> }) =>
      apiClient.post<unknown>(
        `/internal/v1/inventory/counts/${encodeURIComponent(args.sessionId)}/approve`,
        args.body ?? {},
      ),
    onSuccess: (_d, { sessionId }) => {
      qc.invalidateQueries({ queryKey: ["inventory-count-session", sessionId] });
      qc.invalidateQueries({ queryKey: ["inventory-on-hand"] });
    },
  });
}

export function useInventoryRejectCount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { sessionId: string; body?: Record<string, unknown> }) =>
      apiClient.post<unknown>(
        `/internal/v1/inventory/counts/${encodeURIComponent(args.sessionId)}/reject`,
        args.body ?? {},
      ),
    onSuccess: (_d, { sessionId }) => {
      qc.invalidateQueries({ queryKey: ["inventory-count-session", sessionId] });
    },
  });
}

export function useInventoryResolveReconcile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: string; body: Record<string, unknown> }) =>
      apiClient.post<unknown>(
        `/internal/v1/inventory/reconcile/${encodeURIComponent(args.id)}/resolve`,
        args.body,
      ),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["inventory-reconcile-pending"] }),
  });
}

// ── Mutations — requisitions (no list route on BFF; use ledger for movement history) ──

export function useInventoryCreateRequisition() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<unknown>("/internal/v1/inventory/requisitions", body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["inventory-ledger"] }),
  });
}

export function useInventorySubmitRequisition() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiClient.post<unknown>(`/internal/v1/inventory/requisitions/${encodeURIComponent(id)}/submit`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["inventory-ledger"] }),
  });
}

export function useInventoryApproveRequisition() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: string; body: Record<string, unknown> }) =>
      apiClient.post<unknown>(
        `/internal/v1/inventory/requisitions/${encodeURIComponent(args.id)}/approve`,
        args.body,
      ),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["inventory-ledger"] }),
  });
}

export function useInventoryRejectRequisition() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: string; body: Record<string, unknown> }) =>
      apiClient.post<unknown>(
        `/internal/v1/inventory/requisitions/${encodeURIComponent(args.id)}/reject`,
        args.body,
      ),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["inventory-ledger"] }),
  });
}

export function useInventoryFulfillRequisition() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: string; body: Record<string, unknown> }) =>
      apiClient.post<unknown>(
        `/internal/v1/inventory/requisitions/${encodeURIComponent(args.id)}/fulfill`,
        args.body,
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["inventory-ledger"] });
      qc.invalidateQueries({ queryKey: ["inventory-on-hand"] });
    },
  });
}
