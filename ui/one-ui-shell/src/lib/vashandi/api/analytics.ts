import { getVashandi } from "./client";
import type {
  AccessRiskAnalyticsResponse,
  AttendanceAnalyticsResponse,
  RosterCoverageResponse,
  StaffingAnalyticsResponse,
  VashandiActionResponse,
} from "../types";

export async function getStaffingAnalytics(params?: Record<string, string | undefined>) {
  return getVashandi<StaffingAnalyticsResponse>("/analytics/staffing", params);
}

export async function getRosterCoverageAnalytics(params?: Record<string, string | undefined>) {
  return getVashandi<RosterCoverageResponse>("/analytics/roster-coverage", params);
}

export async function getAttendanceAnalytics(params?: Record<string, string | undefined>) {
  return getVashandi<AttendanceAnalyticsResponse>("/analytics/attendance", params);
}

export async function getVacancyAnalytics(params?: Record<string, string | undefined>) {
  return getVashandi("/analytics/vacancies", params);
}

export async function getAccessRiskAnalytics(params?: Record<string, string | undefined>) {
  return getVashandi<AccessRiskAnalyticsResponse>("/analytics/access-risk", params);
}

export function staffingFromResponse(response: VashandiActionResponse): StaffingAnalyticsResponse | undefined {
  if (!response.data || typeof response.data !== "object") return undefined;
  return response.data as StaffingAnalyticsResponse;
}

export function rosterCoverageFromResponse(response: VashandiActionResponse): RosterCoverageResponse | undefined {
  if (!response.data || typeof response.data !== "object") return undefined;
  return response.data as RosterCoverageResponse;
}

export function attendanceAnalyticsFromResponse(response: VashandiActionResponse): AttendanceAnalyticsResponse | undefined {
  if (!response.data || typeof response.data !== "object") return undefined;
  return response.data as AttendanceAnalyticsResponse;
}

export function accessRiskAnalyticsFromResponse(response: VashandiActionResponse): AccessRiskAnalyticsResponse | undefined {
  if (!response.data || typeof response.data !== "object") return undefined;
  return response.data as AccessRiskAnalyticsResponse;
}
