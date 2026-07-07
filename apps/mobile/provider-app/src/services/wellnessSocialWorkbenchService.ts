/**
 * Wellness-Social Workbench service — provider/programme mobile access to the wellness-sovereign
 * social layer (/internal/v1/wellness/social/**), served by Simba (8125) transparently proxied by the
 * Experience BFF WellnessServiceProxyController. DISTINCT from the generic community-service provider
 * social service (providerSocialService.ts, /internal/v1/mobile/provider/social/**).
 *
 * Capabilities: official post + group/community announcement composition, moderation inbox + action,
 * programme-engagement summary (aggregate-only), and flag-to-PCT (safety report → care linkage).
 *
 * NOTE: the mobile vitest runner is not executable in this environment (apps/mobile uses the pnpm
 * workspace:* protocol; npm install fails with EUNSUPPORTEDPROTOCOL). This module is written and
 * type-checked against the shared @impilo/mobile-api-client export surface, mirroring the existing
 * wellnessWorkbenchService.ts; its tests were NOT run here (documented honest partial).
 */
import { apiClient } from "@impilo/mobile-api-client";

const BASE = "/internal/v1/wellness/social";

type Row = Record<string, unknown>;

export interface SocialGroupRef {
  groupId: string;
  name: string;
  memberCount: number;
  visibility: string;
  sensitiveFlag: boolean;
}

export interface ContentReport {
  reportId: string;
  reporterCpid: string;
  subjectType: string;
  subjectId: string;
  subjectOwnerCpid?: string | null;
  reason: string;
  detail?: string | null;
  careLinkageId?: string | null;
  status: string;
  createdAt: string;
}

export interface ProgrammeEngagementSummary {
  groups: number;
  communities: number;
  activeMembers: number;
  posts: number;
  officialPosts: number;
  challengeEnrolment: number;
  challengeCompletions: number;
  challengeCompletionPct: number;
  reportedContent: number;
  moderationBacklog: number;
  safetyEscalations: number;
}

function arr(v: unknown): Row[] {
  return Array.isArray(v) ? (v as Row[]) : [];
}
function str(v: unknown, fallback = ""): string {
  return v != null && v !== "" ? String(v) : fallback;
}
function num(v: unknown): number {
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
}

/** Groups the provider can act in (verified-provider / programme-officer). */
export async function fetchGroups(): Promise<SocialGroupRef[]> {
  const res = await apiClient.get<{ data?: Row[] }>(`${BASE}/groups?mine=true`);
  return arr(res.data?.data).map((g) => ({
    groupId: str(g.groupId ?? g.group_id),
    name: str(g.name),
    memberCount: num(g.memberCount ?? g.member_count),
    visibility: str(g.visibility, "PUBLIC"),
    sensitiveFlag: Boolean(g.sensitiveFlag ?? g.sensitive_flag),
  }));
}

/** Compose an official post in a group (an announcement to the group audience). */
export async function publishGroupAnnouncement(groupId: string, body: string): Promise<void> {
  await apiClient.post(`${BASE}/groups/${encodeURIComponent(groupId)}/announcements`, { body });
}

/** Compose an official community announcement. */
export async function publishCommunityAnnouncement(communityId: string, body: string): Promise<void> {
  await apiClient.post(
    `${BASE}/communities/${encodeURIComponent(communityId)}/announcements`,
    { body },
  );
}

/** Create an official group post (pinned) — reuses the announcement path with an official actor. */
export async function publishOfficialPost(groupId: string, body: string): Promise<void> {
  await publishGroupAnnouncement(groupId, body);
}

/** Moderation inbox for the provider/programme workbench. */
export async function fetchModerationInbox(status = "OPEN"): Promise<{
  reports: ContentReport[];
  backlog: number;
}> {
  const res = await apiClient.get<{ data?: Row[]; backlog?: number }>(
    `${BASE}/moderation/inbox?status=${encodeURIComponent(status)}`,
  );
  const reports = arr(res.data?.data).map((r) => ({
    reportId: str(r.reportId ?? r.report_id),
    reporterCpid: str(r.reporterCpid ?? r.reporter_cpid),
    subjectType: str(r.subjectType ?? r.subject_type),
    subjectId: str(r.subjectId ?? r.subject_id),
    subjectOwnerCpid: (r.subjectOwnerCpid ?? r.subject_owner_cpid) as string | null,
    reason: str(r.reason),
    detail: (r.detail ?? null) as string | null,
    careLinkageId: (r.careLinkageId ?? r.care_linkage_id ?? null) as string | null,
    status: str(r.status),
    createdAt: str(r.createdAt ?? r.created_at),
  }));
  return { reports, backlog: num(res.data?.backlog) };
}

/** Take a moderation action on a report. */
export async function actOnReport(
  reportId: string,
  actionState: string,
  rationale?: string,
): Promise<void> {
  await apiClient.post(`${BASE}/moderation/${encodeURIComponent(reportId)}/action`, {
    action_state: actionState,
    rationale,
  });
}

/**
 * Flag content to PCT — files a SAFETY report which Simba's moderation service escalates to a persisted
 * care linkage via CareLinkageService (never handled socially). The care linkage is the PCT hand-off.
 */
export async function flagToPct(
  subjectType: string,
  subjectId: string,
  subjectOwnerCpid: string,
  reason: "SELF_HARM" | "ABUSE",
  detail?: string,
): Promise<{ escalated: boolean; careLinkageId: string | null }> {
  const res = await apiClient.post<{ data?: Row; escalated?: boolean }>(
    `${BASE}/moderation/reports`,
    {
      subject_type: subjectType,
      subject_id: subjectId,
      subject_owner_cpid: subjectOwnerCpid,
      reason,
      detail,
    },
  );
  const data = (res.data?.data ?? {}) as Row;
  return {
    escalated: Boolean(res.data?.escalated),
    careLinkageId: (data.careLinkageId ?? data.care_linkage_id ?? null) as string | null,
  };
}

/** Aggregate-only programme engagement summary (no per-person content). */
export async function fetchEngagementSummary(
  programmeId?: string,
): Promise<ProgrammeEngagementSummary> {
  const q = programmeId ? `?programme_id=${encodeURIComponent(programmeId)}` : "";
  const res = await apiClient.get<{ data?: Row }>(
    `${BASE}/programme-engagement/dashboard${q}`,
  );
  const d = (res.data?.data ?? {}) as Row;
  return {
    groups: num(d.groups),
    communities: num(d.communities),
    activeMembers: num(d.active_members),
    posts: num(d.posts),
    officialPosts: num(d.official_posts),
    challengeEnrolment: num(d.challenge_enrolment),
    challengeCompletions: num(d.challenge_completions),
    challengeCompletionPct: num(d.challenge_completion_pct),
    reportedContent: num(d.reported_content),
    moderationBacklog: num(d.moderation_backlog),
    safetyEscalations: num(d.safety_escalations),
  };
}
