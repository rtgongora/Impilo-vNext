/**
 * Work Home (Phase G4) — consumes the SAME composition web's /work page reads
 * (`GET /internal/v1/work-home`, Phase E3/E4), so a health worker sees the same
 * governed sections on both platforms rather than a mobile-only reimagining.
 *
 * The BFF always answers 200 when authenticated: a downstream failure is
 * expressed IN-BAND as a section with status DEGRADED, never as an HTTP 5xx.
 * Callers must therefore render section status honestly rather than treating a
 * successful response as "everything is fine" — see WorkHomeScreen.
 */
import { apiClient } from "@impilo/mobile-api-client";

export type WorkHomeSectionStatus = "OK" | "EMPTY" | "DEGRADED";

export interface WorkHomeItem {
  id?: string;
  title?: string;
  description?: string;
  status?: string;
  priority?: string;
  due_at?: string | null;
  href?: string;
  [key: string]: unknown;
}

export interface WorkHomeSection {
  sectionId: string;
  title: string;
  status: WorkHomeSectionStatus;
  items: WorkHomeItem[];
  buckets?: Record<string, WorkHomeItem[]>;
  summary?: Record<string, unknown>;
  /** Human-readable reason for EMPTY/DEGRADED; null when OK. */
  note?: string | null;
  generatedAt?: string;
}

export interface WorkHome {
  contextId: string;
  family: string;
  mode: string;
  friendlyState?: string;
  sections: WorkHomeSection[];
}

interface JsonApiEnvelope<TAttributes> {
  data: { id: string; type: string; attributes: TAttributes };
}

export async function getWorkHome(contextId: string, mode?: string): Promise<WorkHome> {
  const params = new URLSearchParams({ context_id: contextId });
  if (mode) params.set("mode", mode);
  const res = await apiClient.get<JsonApiEnvelope<WorkHome>>(`/internal/v1/work-home?${params.toString()}`);
  return res.data.data.attributes;
}

/** Differential retry of a single section, matching web's per-section retry affordance. */
export async function getWorkHomeSection(
  sectionId: string,
  contextId: string,
  mode?: string
): Promise<WorkHomeSection> {
  const params = new URLSearchParams({ context_id: contextId });
  if (mode) params.set("mode", mode);
  const res = await apiClient.get<JsonApiEnvelope<WorkHomeSection>>(
    `/internal/v1/work-home/sections/${encodeURIComponent(sectionId)}?${params.toString()}`
  );
  return res.data.data.attributes;
}
