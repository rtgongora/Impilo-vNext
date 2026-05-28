/**
 * Facility-scoped operational metrics: beds, wards, queue, staffing roster.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useStaffingRosterWeek } from "@/hooks/queries/useStaffing";

export const facilityOpsKeys = {
  all: (facilityId: string) => ["facility-ops", facilityId] as const,
  wards: (facilityId: string) => [...facilityOpsKeys.all(facilityId), "wards"] as const,
  beds: (facilityId: string) => [...facilityOpsKeys.all(facilityId), "beds"] as const,
  queueWaiting: (facilityId: string) => [...facilityOpsKeys.all(facilityId), "queue", "waiting"] as const,
  queueStats: (facilityId: string) => [...facilityOpsKeys.all(facilityId), "queue", "stats"] as const,
};

export interface WardResource {
  id: string;
  type: string;
  attributes: {
    name: string;
    wardType: string;
    floor: string;
    totalBeds: number;
    availableBeds: number;
    occupiedBeds: number;
    status: string;
  };
}

export interface BedResource {
  id: string;
  type: string;
  attributes: {
    bedNumber: string;
    bedType: string;
    status: string;
    wardId: string;
    wardName: string;
    acuity: string | null;
    patientId: string | null;
    patientName: string | null;
    admittedAt: string | null;
    assignedDoctor: string | null;
  };
}

export interface QueueEntryResource {
  id: string;
  type: string;
  attributes: {
    facility_id: string;
    patient_id: string;
    queue_type: string;
    priority: string | null;
    status: string;
    assigned_to: string | null;
    arrival_time: string;
    reason: string | null;
  };
}

export interface QueueStatsData {
  waiting: number;
  called: number;
  inService: number;
  completed: number;
  noShow: number;
  avgWaitSeconds: number;
}

function mondayISO(d: Date): string {
  const day = d.getDay();
  const diff = d.getDate() - day + (day === 0 ? -6 : 1);
  const monday = new Date(d);
  monday.setDate(diff);
  monday.setHours(0, 0, 0, 0);
  const y = monday.getFullYear();
  const m = String(monday.getMonth() + 1).padStart(2, "0");
  const dayNum = String(monday.getDate()).padStart(2, "0");
  return `${y}-${m}-${dayNum}`;
}

export function useFacilityWards(facilityId: string | undefined) {
  return useQuery({
    queryKey: facilityId ? facilityOpsKeys.wards(facilityId) : ["facility-ops", "wards", "none"],
    queryFn: () =>
      apiClient.get<ApiResponse<WardResource[]>>(`/internal/v1/beds/wards?facility_id=${facilityId}`),
    enabled: !!facilityId,
  });
}

export function useFacilityBeds(facilityId: string | undefined) {
  return useQuery({
    queryKey: facilityId ? facilityOpsKeys.beds(facilityId) : ["facility-ops", "beds", "none"],
    queryFn: () =>
      apiClient.get<ApiResponse<BedResource[]>>(`/internal/v1/beds?facility_id=${facilityId}`),
    enabled: !!facilityId,
  });
}

export function useFacilityQueueWaiting(facilityId: string | undefined, size = 80) {
  return useQuery({
    queryKey: facilityId ? facilityOpsKeys.queueWaiting(facilityId) : ["facility-ops", "queue", "none"],
    queryFn: () =>
      apiClient.get<ApiResponse<QueueEntryResource[]>>(
        `/internal/v1/queue/entries?facility_id=${facilityId}&status=WAITING&size=${size}&page=0`
      ),
    enabled: !!facilityId,
  });
}

export function useFacilityQueueStats(facilityId: string | undefined) {
  return useQuery({
    queryKey: facilityId ? facilityOpsKeys.queueStats(facilityId) : ["facility-ops", "queue-stats", "none"],
    queryFn: () =>
      apiClient.post<{ data: QueueStatsData }>("/internal/v1/queue/entries/stats", {
        facilityId: facilityId!,
      }),
    enabled: !!facilityId,
  });
}

export function useFacilityActiveShiftCount(facilityId: string | undefined) {
  const weekStartISO = mondayISO(new Date());
  const roster = useStaffingRosterWeek({
    facilityId,
    workspaceId: null,
    weekStartISO,
  });
  const count =
    roster.data?.data?.filter((row) => {
      const a = row.attributes;
      return a.status === "ACTIVE" && (a.ended_at == null || a.ended_at === "");
    }).length ?? 0;
  return { ...roster, activeShiftCount: count };
}

export function waitMinutesSince(iso: string): number {
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return 0;
  return Math.max(0, Math.round((Date.now() - t) / 60_000));
}

export function formatWaitLabel(minutes: number): string {
  if (minutes < 1) return "<1 min";
  if (minutes >= 120) return `${Math.round(minutes / 60)} hr`;
  return `${minutes} min`;
}

export function mapQueuePriority(priority: string | null | undefined): "Urgent" | "Normal" | "Low" {
  const p = (priority ?? "").toUpperCase();
  if (p === "URGENT" || p === "EMERGENCY") return "Urgent";
  if (p === "LOW") return "Low";
  return "Normal";
}

export function patientLabel(patientId: string): string {
  if (!patientId) return "Patient";
  const short = patientId.replace(/-/g, "").slice(0, 8);
  return `Patient ${short}`;
}

export interface QueueRowView {
  id: string;
  patientName: string;
  type: string;
  waitTime: string;
  priority: "Urgent" | "Normal" | "Low";
  assignedTo: string | null;
}

export function buildQueueRows(entries: QueueEntryResource[]): QueueRowView[] {
  return entries.map((e) => {
    const a = e.attributes;
    const wm = waitMinutesSince(a.arrival_time);
    return {
      id: e.id,
      patientName: patientLabel(a.patient_id),
      type: a.queue_type || "Queue",
      waitTime: formatWaitLabel(wm),
      priority: mapQueuePriority(a.priority),
      assignedTo: a.assigned_to,
    };
  });
}

export interface WardUtilizationView {
  ward: string;
  total: number;
  occupied: number;
  available: number;
  cleaning: number;
}

export function buildWardUtilization(wards: WardResource[], beds: BedResource[]): WardUtilizationView[] {
  if (beds.length > 0) {
    const byWard = new Map<string, { name: string; counts: Record<string, number>; total: number }>();
    for (const b of beds) {
      const wname = b.attributes.wardName || "Ward";
      const wid = b.attributes.wardId;
      const key = wid || wname;
      if (!byWard.has(key)) {
        byWard.set(key, { name: wname, counts: {}, total: 0 });
      }
      const g = byWard.get(key)!;
      g.total += 1;
      const st = b.attributes.status;
      g.counts[st] = (g.counts[st] ?? 0) + 1;
    }
    return [...byWard.values()].map((g) => ({
      ward: g.name,
      total: g.total,
      occupied: g.counts.OCCUPIED ?? 0,
      available: g.counts.AVAILABLE ?? 0,
      cleaning: g.counts.CLEANING ?? 0,
    }));
  }
  return wards.map((w) => ({
    ward: w.attributes.name,
    total: Number(w.attributes.totalBeds ?? 0),
    occupied: Number(w.attributes.occupiedBeds ?? 0),
    available: Number(w.attributes.availableBeds ?? 0),
    cleaning: 0,
  }));
}

export type AlertTone = "critical" | "warning" | "info";

export interface OpsAlertView {
  id: string;
  type: AlertTone;
  message: string;
  time: string;
}

export function buildOperationalAlerts(params: {
  wards: WardResource[];
  beds: BedResource[];
  queueStats: QueueStatsData | undefined;
  queueEntriesWaiting: QueueEntryResource[];
}): OpsAlertView[] {
  const alerts: OpsAlertView[] = [];
  let seq = 0;
  const push = (type: AlertTone, message: string) => {
    seq += 1;
    alerts.push({ id: `ops-${seq}`, type, message, time: "Live" });
  };

  for (const w of params.wards) {
    const a = w.attributes;
    const total = Number(a.totalBeds ?? 0);
    const avail = Number(a.availableBeds ?? 0);
    if (total > 0 && avail === 0) {
      push("critical", `${a.name}: no beds marked available (capacity exhausted or all reserved).`);
    } else if (total > 0 && avail <= Math.max(1, Math.floor(total * 0.05))) {
      push("warning", `${a.name}: only ${avail} bed(s) available of ${total}.`);
    }
  }

  if (params.beds.length > 0) {
    const occ = params.beds.filter((b) => b.attributes.status === "OCCUPIED").length;
    const tot = params.beds.length;
    const pct = tot > 0 ? Math.round((occ / tot) * 100) : 0;
    if (pct >= 92) {
      push("warning", `Facility bed occupancy at ${pct}% (${occ}/${tot} beds).`);
    }
  }

  const qs = params.queueStats;
  if (qs) {
    if (qs.waiting >= 15) {
      push("warning", `${qs.waiting} patient(s) waiting across queues today.`);
    }
    const avgMin = Math.round((qs.avgWaitSeconds ?? 0) / 60);
    if (avgMin >= 30 && qs.waiting > 0) {
      push("warning", `Average queue wait about ${avgMin} minutes for today's waiting entries.`);
    }
  }

  const longWaits = params.queueEntriesWaiting.filter(
    (e) => waitMinutesSince(e.attributes.arrival_time) >= 40
  );
  if (longWaits.length >= 3) {
    push("warning", `${longWaits.length} patients waiting 40+ minutes (FIFO snapshot).`);
  }

  if (alerts.length === 0) {
    push("info", "No automated capacity or queue threshold breaches detected for the current facility snapshot.");
  }

  return alerts;
}

export interface QueueTypePerfView {
  name: string;
  waiting: number;
  avgWait: number;
  inService: number;
}

/** Row from POST /internal/v1/operations/facility-queue-snapshots */
export type FacilityQueueSnapshotRow = {
  facility_id: string;
  waiting: number;
  called: number;
  inService: number;
  completed: number;
  noShow: number;
  avgWaitSeconds: number;
  total?: number;
  source?: string;
  error?: string;
};

export function useFacilityQueueSnapshots(facilityIds: string[] | undefined) {
  return useQuery({
    queryKey: ["facility-queue-snapshots", facilityIds?.join(",") ?? ""],
    enabled: !!facilityIds && facilityIds.length > 0 && facilityIds.length <= 40,
    queryFn: () =>
      apiClient.post<{ data: FacilityQueueSnapshotRow[] }>(
        "/internal/v1/operations/facility-queue-snapshots",
        { facility_ids: facilityIds },
      ),
  });
}

export function buildQueuePerformanceByType(entries: QueueEntryResource[]): QueueTypePerfView[] {
  const map = new Map<string, { waiting: number; sumMin: number; n: number }>();
  for (const e of entries) {
    const t = e.attributes.queue_type || "General";
    const g = map.get(t) ?? { waiting: 0, sumMin: 0, n: 0 };
    g.waiting += 1;
    g.sumMin += waitMinutesSince(e.attributes.arrival_time);
    g.n += 1;
    map.set(t, g);
  }
  return [...map.entries()]
    .map(([name, v]) => ({
      name,
      waiting: v.waiting,
      avgWait: v.n > 0 ? Math.round(v.sumMin / v.n) : 0,
      inService: 0,
    }))
    .sort((a, b) => b.waiting - a.waiting)
    .slice(0, 8);
}
