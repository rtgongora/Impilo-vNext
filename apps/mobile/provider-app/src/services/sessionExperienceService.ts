/**
 * Session Experience Contract — mobile parity with web shell (`SessionExperienceController`).
 */

import { apiClient } from "@impilo/mobile-api-client";

export interface MobileSessionExperienceContract {
  authenticated: boolean;
  workTabVisible: boolean;
  visibleVashandiWorkspaces: string[];
  blockedVashandiWorkspaces: string[];
  vashandiFriendlyResolutionState?: string;
  vashandiIntegrationStatus?: string;
  vashandiWorkforceProfileId?: string;
  currentWorkforceStatus?: string;
  attendanceStatus?: string;
  leaveAvailabilityStatus?: string;
  activeRosteredShifts?: Array<Record<string, unknown>>;
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function readString(row: Record<string, unknown>, key: string): string | undefined {
  const value = row[key];
  return value != null && String(value).trim() !== "" ? String(value) : undefined;
}

function readStringList(row: Record<string, unknown>, key: string): string[] {
  const value = row[key];
  if (!Array.isArray(value)) return [];
  return value.map((item) => String(item));
}

export async function fetchSessionExperienceContract(): Promise<MobileSessionExperienceContract> {
  const r = await apiClient.get<{ data?: { attributes?: Record<string, unknown> } }>(
    "/internal/v1/session/experience",
  );
  const attrs = asRecord(r.data?.data?.attributes ?? r.data);

  const tabs = asRecord(attrs.tabs);
  const work = asRecord(tabs.work);

  return {
    authenticated: attrs.authenticated === true,
    workTabVisible: work.visible !== false,
    visibleVashandiWorkspaces: readStringList(attrs, "visibleVashandiWorkspaces"),
    blockedVashandiWorkspaces: readStringList(attrs, "blockedVashandiWorkspaces"),
    vashandiFriendlyResolutionState: readString(attrs, "vashandiFriendlyResolutionState"),
    vashandiIntegrationStatus: readString(attrs, "vashandiIntegrationStatus"),
    vashandiWorkforceProfileId: readString(attrs, "vashandiWorkforceProfileId"),
    currentWorkforceStatus: readString(attrs, "currentWorkforceStatus"),
    attendanceStatus: readString(attrs, "attendanceStatus"),
    leaveAvailabilityStatus: readString(attrs, "leaveAvailabilityStatus"),
    activeRosteredShifts: Array.isArray(attrs.activeRosteredShifts)
      ? (attrs.activeRosteredShifts as Array<Record<string, unknown>>)
      : [],
  };
}
