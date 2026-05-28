import { apiClient } from "@impilo/mobile-api-client";
import type { ClinicalWorklistItem, ClinicalWorklistSummary } from "../types";

interface WorklistResource {
  data: {
    items: Array<Record<string, unknown>>;
    summary: {
      total: number;
      urgent: number;
      overdue: number;
      by_source?: Record<string, number>;
    };
  };
}

function mapItem(row: Record<string, unknown>): ClinicalWorklistItem {
  return {
    id: String(row.id ?? ""),
    kind: String(row.kind ?? "ITEM"),
    source: String(row.source ?? "unknown"),
    title: String(row.title ?? "Clinical action"),
    description: row.description ? String(row.description) : undefined,
    priority: row.priority ? String(row.priority) : undefined,
    status: row.status ? String(row.status) : undefined,
    dueAt: row.due_at ? String(row.due_at) : undefined,
    createdAt: row.created_at ? String(row.created_at) : undefined,
    patientId: row.patient_id ? String(row.patient_id) : undefined,
    href: row.href ? String(row.href) : undefined,
  };
}

export async function getClinicalWorklist(
  facilityId: string,
  assigneeId?: string,
  size: number = 24
): Promise<{ items: ClinicalWorklistItem[]; summary: ClinicalWorklistSummary }> {
  const params = new URLSearchParams({ facility_id: facilityId, size: String(size) });
  if (assigneeId) params.set("assignee_id", assigneeId);
  const response = await apiClient.get<WorklistResource>(
    `/internal/v1/mobile/provider/clinical-worklist?${params.toString()}`
  );

  const items = (response.data.data.items ?? []).map(mapItem);
  const summaryRaw = response.data.data.summary;
  const summary: ClinicalWorklistSummary = {
    total: Number(summaryRaw.total ?? items.length),
    urgent: Number(summaryRaw.urgent ?? 0),
    overdue: Number(summaryRaw.overdue ?? 0),
    bySource: summaryRaw.by_source ?? {},
  };
  return { items, summary };
}
