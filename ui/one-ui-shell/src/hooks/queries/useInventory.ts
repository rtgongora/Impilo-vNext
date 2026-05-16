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

// ── Legacy dashboard shapes (maps sovereign ApiResponse + PagedResponse) ──

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function extractDataArray(raw: unknown): Record<string, unknown>[] {
  const data = asRecord(raw).data;
  return Array.isArray(data) ? (data as Record<string, unknown>[]) : [];
}

export interface InventoryItem {
  id: string;
  facilityId: string;
  productCode: string;
  productName: string;
  category: string;
  quantityOnHand: number;
  reorderLevel: number;
  unit: string;
  status: string;
  lastCountedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface InventoryMovement {
  id: string;
  movedAt: string;
  itemName: string;
  fromLocation: string;
  toLocation: string;
  quantity: number;
  reason: string;
  performedBy: string;
}

export interface InventoryRequisition {
  id: string;
  requisitionNumber: string;
  requestedBy: string;
  itemCount: number;
  status: string;
  neededBy: string | null;
  notes: string;
  createdAt: string;
}

export interface InventoryCount {
  id: string;
  countDate: string;
  countedBy: string;
  itemCount: number;
  discrepancies: number;
  status: string;
  notes: string;
}

function str(v: unknown): string {
  if (v === null || v === undefined) return "";
  return String(v);
}

/** On-hand rows for facility (catalog + quantity projection). */
export function useInventoryItems(facilityId: string) {
  return useQuery({
    queryKey: ["inventory-items", facilityId],
    queryFn: async () => {
      const raw = await apiClient.get<unknown>(
        `/internal/v1/inventory/items${q({ facility_id: facilityId })}`,
      );
      return extractDataArray(raw).map((resource) => {
        const attrs = asRecord(resource.attributes);
        return {
          id: str(resource.id),
          facilityId: str(attrs.facility_id) || facilityId,
          productCode: str(attrs.product_code),
          productName: str(attrs.product_name),
          category: str(attrs.category),
          quantityOnHand: Number(attrs.quantity_on_hand ?? 0) || 0,
          reorderLevel: Number(attrs.reorder_level ?? 0) || 0,
          unit: str(attrs.unit) || "EA",
          status: str(attrs.status) || "ACTIVE",
          lastCountedAt: (attrs.last_counted_at as string | null) ?? null,
          createdAt: str(attrs.created_at),
          updatedAt: str(attrs.updated_at),
        } satisfies InventoryItem;
      });
    },
    enabled: !!facilityId,
  });
}

/** Ledger events as movement rows. */
export function useInventoryMovements(facilityId: string) {
  return useQuery({
    queryKey: ["inventory-movements", facilityId],
    queryFn: async () => {
      const raw = await apiClient.get<unknown>(
        `/internal/v1/inventory/movements${q({ facility_id: facilityId })}`,
      );
      return extractDataArray(raw).map((resource) => {
        const attrs = asRecord(resource.attributes);
        return {
          id: str(resource.id),
          movedAt: str(attrs.moved_at),
          itemName: str(attrs.item_name),
          fromLocation: str(attrs.from_location) || "—",
          toLocation: str(attrs.to_location) || "—",
          quantity: Number(attrs.quantity ?? 0) || 0,
          reason: str(attrs.reason) || "—",
          performedBy: str(attrs.performed_by) || "—",
        } satisfies InventoryMovement;
      });
    },
    enabled: !!facilityId,
  });
}

/** Facility requisitions list. */
export function useInventoryRequisitions(facilityId: string) {
  return useQuery({
    queryKey: ["inventory-requisitions", facilityId],
    queryFn: async (): Promise<InventoryRequisition[]> => {
      const raw = await apiClient.get<unknown>(
        `/internal/v1/inventory/requisitions${q({ facility_id: facilityId })}`,
      );
      return extractDataArray(raw).map((resource) => {
        const attrs = asRecord(resource.attributes);
        return {
          id: str(resource.id),
          requisitionNumber: str(attrs.requisition_number),
          requestedBy: str(attrs.requested_by),
          itemCount: Number(attrs.item_count ?? 0) || 0,
          status: str(attrs.status),
          neededBy: (attrs.needed_by as string | null) ?? null,
          notes: str(attrs.notes),
          createdAt: str(attrs.created_at),
        } satisfies InventoryRequisition;
      });
    },
    enabled: !!facilityId,
  });
}

/** No facility-scoped count listing yet — physical count UI uses local draft rows. */
export function useInventoryCounts(_facilityId: string) {
  return useQuery({
    queryKey: ["inventory-counts", _facilityId],
    queryFn: async (): Promise<InventoryCount[]> => [],
    enabled: !!_facilityId,
  });
}

export interface CreateInventoryItemPayload {
  facilityId: string;
  productCode: string;
  productName: string;
  category: string;
  unit: string;
  reorderLevel: number;
  quantityOnHand?: number;
}

function extractItemDto(raw: unknown): Record<string, unknown> {
  return asRecord(asRecord(raw).data);
}

function mapItemDtoToInventoryItem(o: Record<string, unknown>, facilityId: string): InventoryItem {
  const created = str(o.createdAt);
  return {
    id: str(o.itemId),
    facilityId,
    productCode: str(o.itemCode),
    productName: str(o.name),
    category: str(o.category) || "",
    quantityOnHand: 0,
    reorderLevel: 0,
    unit: str(o.uom) || "EA",
    status: o.active === false ? "INACTIVE" : "ACTIVE",
    lastCountedAt: null,
    createdAt: created,
    updatedAt: str(o.updatedAt) || created,
  };
}

export function useCreateInventoryItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (payload: CreateInventoryItemPayload) => {
      const body = {
        itemCode: payload.productCode,
        name: payload.productName,
        uom: payload.unit || "EA",
        barcode: null as string | null,
        category: payload.category || null,
        controlled: false,
        ziboRefsJson: null as string | null,
      };
      const raw = await apiClient.post<unknown>("/internal/v1/inventory/items", body);
      return mapItemDtoToInventoryItem(extractItemDto(raw), payload.facilityId);
    },
    onSuccess: (_data, payload) => {
      qc.invalidateQueries({ queryKey: ["inventory-items", payload.facilityId] });
      qc.invalidateQueries({ queryKey: ["inventory-on-hand", payload.facilityId] });
    },
  });
}

export interface CreateInventoryRequisitionPayload {
  facilityId: string;
  requestedBy: string;
  itemCount: number;
  neededBy?: string;
  notes?: string;
  requisitionNumber?: string;
}

export function useCreateInventoryRequisition() {
  const qc = useQueryClient();
  return useMutation<unknown, unknown, CreateInventoryRequisitionPayload>({
    mutationFn: async (payload) => {
      return apiClient.post("/internal/v1/inventory/requisitions", {
        facility_id: payload.facilityId,
        requested_by: payload.requestedBy,
        item_count: payload.itemCount,
        needed_by: payload.neededBy,
        notes: payload.notes,
        requisition_number: payload.requisitionNumber,
      });
    },
    onSuccess: (_data, payload) => {
      qc.invalidateQueries({ queryKey: ["inventory-requisitions", payload.facilityId] });
    },
  });
}

export interface PatchRequisitionPayload {
  facilityId: string;
  requisitionId: string;
  status: string;
  notes?: string;
}

export function usePatchRequisitionStatus() {
  const qc = useQueryClient();
  return useMutation<unknown, unknown, PatchRequisitionPayload>({
    mutationFn: (payload: PatchRequisitionPayload) =>
      apiClient.patch(`/internal/v1/inventory/requisitions/${payload.requisitionId}/status`, {
        status: payload.status,
        notes: payload.notes,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["inv"] });
    },
  });
}
