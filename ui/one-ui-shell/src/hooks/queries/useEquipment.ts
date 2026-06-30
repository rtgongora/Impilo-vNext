/**
 * Experience UI — Equipment operations (asset-registry-service via BFF, WS#5).
 *
 * Proxies `/internal/v1/equipment/**` (BFF `EquipmentOperationsBffController`).
 * Covers registry, custody transfer, maintenance, calibration, fault, IoT alerts,
 * service readiness, deployment kits and asset audits. Real data only — no fixtures.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

const BASE = "/internal/v1/equipment";

/** Tolerant row extractor for `{ items: [...] }` envelopes returned by the equipment BFF lane. */
export function equipmentItems(raw: unknown): Record<string, unknown>[] {
  if (raw && typeof raw === "object" && Array.isArray((raw as { items?: unknown[] }).items)) {
    return (raw as { items: Record<string, unknown>[] }).items;
  }
  return [];
}

function q(params: Record<string, string | number | undefined | null>): string {
  const usp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === "") continue;
    usp.set(k, String(v));
  }
  const s = usp.toString();
  return s ? `?${s}` : "";
}

export interface EquipmentRow {
  equipment_id: string;
  facility_id: string;
  name: string;
  equipment_type: string;
  serial_number?: string | null;
  tag?: string | null;
  status: string;
  criticality: string;
  asset_class: string;
  regulated: boolean;
  custodian_ref?: string | null;
  device_id?: string | null;
  next_calibration?: string | null;
  next_maintenance?: string | null;
}

export interface EquipmentListResponse {
  items: EquipmentRow[];
  total_elements: number;
  has_more: boolean;
}

export function useEquipmentList(opts?: { facilityId?: string; page?: number; size?: number }) {
  return useQuery({
    queryKey: ["equipment", "list", opts],
    queryFn: () =>
      apiClient.get<EquipmentListResponse>(
        `${BASE}${q({ facility_id: opts?.facilityId, page: opts?.page ?? 0, size: opts?.size ?? 50 })}`,
      ),
  });
}

export function useEquipmentDetail(equipmentId?: string | null) {
  return useQuery({
    queryKey: ["equipment", "detail", equipmentId],
    queryFn: () => apiClient.get<unknown>(`${BASE}/${encodeURIComponent(equipmentId!)}/detail`),
    enabled: !!equipmentId,
  });
}

export function useRegisterEquipment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post<unknown>(BASE, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["equipment", "list"] }),
  });
}

export function useChangeEquipmentStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (a: { equipmentId: string; body: Record<string, unknown> }) =>
      apiClient.post<unknown>(`${BASE}/${encodeURIComponent(a.equipmentId)}/status`, a.body),
    onSuccess: (_d, a) => {
      qc.invalidateQueries({ queryKey: ["equipment", "detail", a.equipmentId] });
      qc.invalidateQueries({ queryKey: ["equipment", "list"] });
    },
  });
}

export function useInitiateTransfer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (a: { equipmentId: string; body: Record<string, unknown> }) =>
      apiClient.post<unknown>(`${BASE}/${encodeURIComponent(a.equipmentId)}/transfers`, a.body),
    onSuccess: (_d, a) => qc.invalidateQueries({ queryKey: ["equipment", "detail", a.equipmentId] }),
  });
}

export function useConfirmReceipt() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (transferId: string) =>
      apiClient.post<unknown>(`${BASE}/transfers/${encodeURIComponent(transferId)}/receive`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["equipment"] }),
  });
}

// -- Maintenance --

export function useMaintenanceDue() {
  return useQuery({
    queryKey: ["equipment", "maintenance-due"],
    queryFn: () => apiClient.get<{ items: EquipmentRow[] }>(`${BASE}/maintenance/due`),
  });
}

export function useOpenMaintenance() {
  return useQuery({
    queryKey: ["equipment", "maintenance-open"],
    queryFn: () => apiClient.get<{ items: unknown[] }>(`${BASE}/maintenance/open`),
  });
}

export function useOpenMaintenanceTask() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (a: { equipmentId: string; body: Record<string, unknown> }) =>
      apiClient.post<unknown>(`${BASE}/${encodeURIComponent(a.equipmentId)}/maintenance`, a.body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["equipment"] }),
  });
}

export function useCompleteMaintenance() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (a: { taskId: string; body: Record<string, unknown> }) =>
      apiClient.post<unknown>(`${BASE}/maintenance/${encodeURIComponent(a.taskId)}/complete`, a.body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["equipment"] }),
  });
}

// -- Calibration --

export function useCalibrationDue() {
  return useQuery({
    queryKey: ["equipment", "calibration-due"],
    queryFn: () => apiClient.get<{ items: EquipmentRow[] }>(`${BASE}/calibration/due`),
  });
}

export function useRecordCalibration() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (a: { equipmentId: string; body: Record<string, unknown> }) =>
      apiClient.post<unknown>(`${BASE}/${encodeURIComponent(a.equipmentId)}/calibration`, a.body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["equipment"] }),
  });
}

// -- Fault --

export function useReportFault() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (a: { equipmentId: string; body: Record<string, unknown> }) =>
      apiClient.post<unknown>(`${BASE}/${encodeURIComponent(a.equipmentId)}/faults`, a.body),
    onSuccess: (_d, a) => qc.invalidateQueries({ queryKey: ["equipment", "detail", a.equipmentId] }),
  });
}

// -- IoT alerts --

export function useActiveAlerts() {
  return useQuery({
    queryKey: ["equipment", "alerts-active"],
    queryFn: () => apiClient.get<{ items: unknown[] }>(`${BASE}/alerts/active`),
  });
}

export function useResolveAlert() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (alertId: string) =>
      apiClient.post<unknown>(`${BASE}/alerts/${encodeURIComponent(alertId)}/resolve`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["equipment", "alerts-active"] }),
  });
}

export function useNotifyAlert() {
  return useMutation({
    mutationFn: (a: { alertId: string; body?: Record<string, unknown> }) =>
      apiClient.post<unknown>(`${BASE}/alerts/${encodeURIComponent(a.alertId)}/notify`, a.body ?? {}),
  });
}

// -- Readiness --

export interface ReadinessRequirement {
  equipment_type: string;
  required: number;
  available: number;
  met: boolean;
  gap: string;
}
export interface ReadinessProfileResult {
  service_point: string;
  name: string;
  ready: boolean;
  requirements: ReadinessRequirement[];
}
export interface ReadinessResponse {
  facility_ref: string;
  ready: boolean;
  total_gaps: number;
  profiles: ReadinessProfileResult[];
}

export function useReadiness(facilityRef?: string | null) {
  return useQuery({
    queryKey: ["equipment", "readiness", facilityRef],
    queryFn: () =>
      apiClient.get<ReadinessResponse>(`${BASE}/readiness${q({ facility_ref: facilityRef })}`),
    enabled: !!facilityRef,
  });
}

export function useCreateReadinessProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<unknown>(`${BASE}/readiness/profiles`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["equipment", "readiness"] }),
  });
}

// -- Deployment kits --

export function useKits() {
  return useQuery({
    queryKey: ["equipment", "kits"],
    queryFn: () => apiClient.get<{ items: unknown[] }>(`${BASE}/deployment-kits`),
  });
}

export function useCreateKit() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<unknown>(`${BASE}/deployment-kits`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["equipment", "kits"] }),
  });
}

export function useReturnKit() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (a: { kitId: string; body: Record<string, unknown> }) =>
      apiClient.post<unknown>(`${BASE}/deployment-kits/${encodeURIComponent(a.kitId)}/return`, a.body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["equipment", "kits"] }),
  });
}

// -- Asset audit --

export function useAudits(facilityRef?: string | null) {
  return useQuery({
    queryKey: ["equipment", "audits", facilityRef],
    queryFn: () => apiClient.get<{ items: unknown[] }>(`${BASE}/audits${q({ facility_ref: facilityRef })}`),
    enabled: !!facilityRef,
  });
}

export function useOpenAudit() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post<unknown>(`${BASE}/audits`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["equipment", "audits"] }),
  });
}

export function useConfirmAuditItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (a: { itemId: string; body: Record<string, unknown> }) =>
      apiClient.post<unknown>(`${BASE}/audits/items/${encodeURIComponent(a.itemId)}`, a.body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["equipment", "audits"] }),
  });
}
