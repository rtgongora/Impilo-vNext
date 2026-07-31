import { apiClient } from "@impilo/mobile-api-client";

const BASE = "/internal/v1/mental-health";

export type MentalHealthReferral = Record<string, unknown> & {
  id?: string;
  episode_id?: string;
  subject_cpid?: string | null;
  status?: "PENDING" | "ACCEPTED" | "DECLINED";
  request_reason?: string | null;
};

function unwrap<T>(res: unknown): T {
  const body = res as { data?: T } | T;
  return body && typeof body === "object" && "data" in (body as object)
    ? (body as { data: T }).data
    : (body as T);
}

export async function listMentalHealthReferrals(status: "PENDING" | "ACCEPTED"): Promise<MentalHealthReferral[]> {
  const res = await apiClient.get(`${BASE}/referrals?status=${status}`);
  return unwrap<MentalHealthReferral[]>(res.data) ?? [];
}

export async function getMentalHealthReferral(referralId: string): Promise<MentalHealthReferral> {
  const res = await apiClient.get(`${BASE}/referrals/${referralId}`);
  return unwrap<MentalHealthReferral>(res.data);
}

export async function acceptMentalHealthReferral(referralId: string) {
  return apiClient.post(`${BASE}/referrals/${referralId}/accept`, {});
}

export async function declineMentalHealthReferral(referralId: string, reason: string) {
  return apiClient.post(`${BASE}/referrals/${referralId}/decline`, { reason });
}

export async function listAssessments(referralId: string) {
  const res = await apiClient.get(`${BASE}/referrals/${referralId}/assessments`);
  return unwrap<Record<string, unknown>[]>(res.data) ?? [];
}

export async function recordAssessment(referralId: string, body: Record<string, unknown>) {
  return apiClient.post(`${BASE}/referrals/${referralId}/assessments`, body);
}

export async function listRiskFormulations(assessmentId: string) {
  const res = await apiClient.get(`${BASE}/assessments/${assessmentId}/risk-formulation`);
  return unwrap<Record<string, unknown>[]>(res.data) ?? [];
}

export async function recordRiskFormulation(assessmentId: string, body: Record<string, unknown>) {
  return apiClient.post(`${BASE}/assessments/${assessmentId}/risk-formulation`, body);
}

export async function listSafetyPlans(referralId: string) {
  const res = await apiClient.get(`${BASE}/referrals/${referralId}/safety-plans`);
  return unwrap<Record<string, unknown>[]>(res.data) ?? [];
}

export async function recordSafetyPlan(referralId: string, body: Record<string, unknown>) {
  return apiClient.post(`${BASE}/referrals/${referralId}/safety-plans`, body);
}

export async function listInvoluntaryEpisodes(referralId: string) {
  const res = await apiClient.get(`${BASE}/referrals/${referralId}/involuntary-episodes`);
  return unwrap<Record<string, unknown>[]>(res.data) ?? [];
}

export async function openInvoluntaryEpisode(referralId: string, body: Record<string, unknown>) {
  return apiClient.post(`${BASE}/referrals/${referralId}/involuntary-episodes`, body);
}

export async function closeInvoluntaryEpisode(episodeId: string, endedReason: string) {
  return apiClient.post(`${BASE}/involuntary-episodes/${episodeId}/close`, { endedReason });
}

export async function listRestraintEvents(referralId: string) {
  const res = await apiClient.get(`${BASE}/referrals/${referralId}/restraint-events`);
  return unwrap<Record<string, unknown>[]>(res.data) ?? [];
}

export async function recordRestraintEvent(referralId: string, body: Record<string, unknown>) {
  return apiClient.post(`${BASE}/referrals/${referralId}/restraint-events`, body);
}

export async function endRestraintEvent(eventId: string) {
  return apiClient.post(`${BASE}/restraint-events/${eventId}/end`, {});
}

export async function reviewRestraintEvent(eventId: string, body: Record<string, unknown>) {
  return apiClient.post(`${BASE}/restraint-events/${eventId}/review`, body);
}

export async function listAdmissionRequests(referralId: string) {
  const res = await apiClient.get(`${BASE}/referrals/${referralId}/admission-requests`);
  return unwrap<Record<string, unknown>[]>(res.data) ?? [];
}

export async function raiseAdmissionRequest(referralId: string, body: Record<string, unknown>) {
  return apiClient.post(`${BASE}/referrals/${referralId}/admission-requests`, body);
}

export async function acknowledgeAdmissionRequest(requestId: string, pctAdmissionId: string) {
  return apiClient.post(`${BASE}/admission-requests/${requestId}/acknowledge`, { pctAdmissionId });
}

export async function listFollowups(referralId: string) {
  const res = await apiClient.get(`${BASE}/referrals/${referralId}/followups`);
  return unwrap<Record<string, unknown>[]>(res.data) ?? [];
}

export async function scheduleFollowup(referralId: string, body: Record<string, unknown>) {
  return apiClient.post(`${BASE}/referrals/${referralId}/followups`, body);
}

export async function completeFollowup(followupId: string, outcomeNotes?: string) {
  return apiClient.post(`${BASE}/followups/${followupId}/complete`, { outcomeNotes });
}
