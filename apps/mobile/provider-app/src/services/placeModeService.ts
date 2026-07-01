/**
 * Place Mode Service — Indawo public-health place operations for provider/outreach cadres.
 *
 * Backend: experience-bff place-mode (the same live routes the web Indawo surfaces consume via
 * ui/one-ui-shell/src/components/facility-mode/usePlaceMode.ts):
 *   - GET  /internal/v1/place-mode/summary
 *   - GET  /internal/v1/place-mode/alerts[?status=]
 *   - GET  /internal/v1/place-mode/outbreaks[?status=]
 *   - GET  /internal/v1/place-mode/field-teams
 *   - POST /internal/v1/place-mode/outbreaks
 *   - POST /internal/v1/place-mode/field-teams/{teamId}/deploy
 *
 * Closes part of GAP-19 (Indawo place mode had no mobile surface). Real data only — no mock rows.
 */

import { apiClient } from "@impilo/mobile-api-client";

const BASE = "/internal/v1/place-mode";

export interface PlaceModeSummary {
  openAlerts: number;
  activeOutbreaks: number;
  teams: number;
}

export interface SurveillanceAlert {
  alertId: string;
  siteId: string | null;
  signalType: string;
  conditionCode: string | null;
  severity: string;
  status: string;
  caseCount: number;
  description: string | null;
  detectedAt: string | null;
}

export interface Outbreak {
  outbreakId: string;
  conditionCode: string;
  name: string;
  severity: string;
  status: string;
  areaDescription: string | null;
  caseCount: number;
  deathCount: number;
  declaredAt: string | null;
}

export interface FieldTeam {
  teamId: string;
  name: string;
  teamType: string;
  status: string;
  leadHealthId: string | null;
  memberCount: number;
}

export interface CreateOutbreakInput {
  conditionCode: string;
  name: string;
  severity?: string;
  areaDescription?: string;
}

export async function fetchPlaceModeSummary(): Promise<PlaceModeSummary> {
  const response = await apiClient.get<{ data: PlaceModeSummary }>(`${BASE}/summary`);
  return response.data.data;
}

export async function fetchSurveillanceAlerts(status?: string): Promise<SurveillanceAlert[]> {
  const qs = status ? `?status=${encodeURIComponent(status)}` : "";
  const response = await apiClient.get<{ data: SurveillanceAlert[] }>(`${BASE}/alerts${qs}`);
  return response.data.data;
}

export async function fetchOutbreaks(status?: string): Promise<Outbreak[]> {
  const qs = status ? `?status=${encodeURIComponent(status)}` : "";
  const response = await apiClient.get<{ data: Outbreak[] }>(`${BASE}/outbreaks${qs}`);
  return response.data.data;
}

export async function fetchFieldTeams(): Promise<FieldTeam[]> {
  const response = await apiClient.get<{ data: FieldTeam[] }>(`${BASE}/field-teams`);
  return response.data.data;
}

export async function createOutbreak(input: CreateOutbreakInput): Promise<Outbreak> {
  const response = await apiClient.post<{ data: Outbreak }>(`${BASE}/outbreaks`, input);
  return response.data.data;
}

export async function deployFieldTeam(input: {
  teamId: string;
  outbreakId?: string;
  siteId?: string;
  objective?: string;
}): Promise<void> {
  await apiClient.post(`${BASE}/field-teams/${encodeURIComponent(input.teamId)}/deploy`, {
    outbreakId: input.outbreakId,
    siteId: input.siteId,
    objective: input.objective,
  });
}
