/**
 * Regulatory self-service + public explore + student registration lanes.
 * Person journeys only — no operator reconcile/review desks.
 */

import { apiClient } from "./client";
import { publicApiClient } from "./publicClient";

function unwrap<T>(payload: unknown): T {
  const p = (payload as { data?: unknown })?.data ?? payload;
  return p as T;
}

function unwrapList<T>(payload: unknown): T[] {
  const p = (payload as { data?: unknown })?.data ?? payload;
  return Array.isArray(p) ? (p as T[]) : [];
}

export interface MyRegulatorySummary {
  linked: boolean;
  providerPublicId?: string | null;
  councils: Array<{
    councilCode: string;
    councilName: string;
    registrationNumber?: string | null;
    status: string;
    standing: string;
    restrictions?: Array<{ type: string; description: string; status: string }>;
  }>;
}

export interface MyRenewalEligibility {
  linked: boolean;
  eligible: boolean;
  cpdRequirementMet: boolean;
  earnedPoints: number;
  requiredPoints: number;
  outstanding: string[];
}

export interface MyRegulatoryApplication {
  id: number;
  applicationType: string;
  workflowState: string;
  councilCode: string;
  submittedAt?: string | null;
}

export interface MyRegulatoryComplaint {
  id: number | string;
  status?: string;
  councilCode?: string;
  summary?: string;
  openedAt?: string | null;
}

export interface ApplicationSection {
  sectionKey: string;
  label: string;
  sequenceNo: number;
  state: string;
  content: Record<string, unknown> | null;
  returnedReason: string | null;
  returnCount: number;
}

export interface PublicCouncil {
  councilCode?: string;
  name?: string;
  councilType?: string;
  description?: string;
}

export interface PublicRegister {
  registerCode?: string;
  name?: string;
  status?: string;
}

export async function getMyRegulatorySummary(): Promise<MyRegulatorySummary> {
  const res = await apiClient.get<unknown>("/internal/v1/me/regulatory/summary");
  return unwrap<MyRegulatorySummary>(res.data);
}

export async function getMyRenewalEligibility(): Promise<MyRenewalEligibility> {
  const res = await apiClient.get<unknown>("/internal/v1/me/regulatory/renewal-eligibility");
  return unwrap<MyRenewalEligibility>(res.data);
}

export async function getMyRegulatoryApplications(): Promise<MyRegulatoryApplication[]> {
  const res = await apiClient.get<unknown>("/internal/v1/me/regulatory/applications");
  return unwrapList<MyRegulatoryApplication>(res.data);
}

export async function getMyRegulatoryComplaints(): Promise<MyRegulatoryComplaint[]> {
  const res = await apiClient.get<unknown>("/internal/v1/me/regulatory/complaints");
  return unwrapList<MyRegulatoryComplaint>(res.data);
}

export async function getMyPracticeEstablishments(): Promise<unknown[]> {
  const res = await apiClient.get<unknown>("/internal/v1/me/regulatory/practice-establishments");
  return unwrapList<unknown>(res.data);
}

export async function getStudentApplicationSections(applicationId: string): Promise<ApplicationSection[]> {
  const res = await apiClient.get<unknown>(
    `/api/v1/student-registration/applications/${encodeURIComponent(applicationId)}/sections`,
  );
  return unwrapList<ApplicationSection>(res.data);
}

export async function resubmitStudentSection(
  applicationId: string,
  sectionKey: string,
  content: Record<string, unknown>,
): Promise<ApplicationSection> {
  const res = await apiClient.post<unknown>(
    `/api/v1/student-registration/applications/${encodeURIComponent(applicationId)}/sections/${encodeURIComponent(sectionKey)}/resubmit`,
    content,
  );
  return unwrap<ApplicationSection>(res.data);
}

export async function getContributionSections(inviteId: string): Promise<ApplicationSection[]> {
  const res = await apiClient.get<unknown>(
    `/api/v1/student-registration/contributions/${encodeURIComponent(inviteId)}/sections`,
  );
  return unwrapList<ApplicationSection>(res.data);
}

export async function completeContributionSection(
  inviteId: string,
  sectionKey: string,
  content: Record<string, unknown>,
): Promise<ApplicationSection> {
  const res = await apiClient.post<unknown>(
    `/api/v1/student-registration/contributions/${encodeURIComponent(inviteId)}/sections/${encodeURIComponent(sectionKey)}`,
    content,
  );
  return unwrap<ApplicationSection>(res.data);
}

export async function getPublicCouncils(): Promise<PublicCouncil[]> {
  const res = await publicApiClient.get<unknown>("/internal/v1/public/gateway/regulatory/councils");
  return unwrapList<PublicCouncil>(res.data);
}

export async function getPublicCouncilRegisters(councilCode: string): Promise<PublicRegister[]> {
  const res = await publicApiClient.get<unknown>(
    `/internal/v1/public/gateway/regulatory/councils/${encodeURIComponent(councilCode)}/registers`,
  );
  const payload = unwrap<{ registers?: PublicRegister[] } | PublicRegister[]>(res.data);
  if (Array.isArray(payload)) return payload;
  return Array.isArray(payload?.registers) ? payload.registers : [];
}
