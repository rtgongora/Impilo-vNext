/**
 * Impilo Live — Live Events & Broadcast
 *
 * Frontend API surface for services/live-service. All requests go through
 * `apiClient` so trust headers, correlation IDs, and step-up handling apply
 * uniformly. The Live backend mounts at /internal/v1/live.
 */

import { apiClient, type ApiResponse } from "./api-client";

// ============================================================================
// Domain types (mirror live-service DTOs / entities)
// ============================================================================

export type LiveEventStatus =
  | "DRAFT"
  | "SCHEDULED"
  | "LIVE"
  | "ENDED"
  | "PROCESSING_REPLAY"
  | "PUBLISHED_REPLAY"
  | "CANCELLED"
  | string;

export type LiveContextType = "PROFESSIONAL" | "CITIZEN" | "PUBLIC" | string;

export type LiveMode =
  | "CLINICAL_SESSION"
  | "PROFESSIONAL_MEETING"
  | "WEBINAR_CPD"
  | "PUBLIC_BROADCAST"
  | "HYBRID_EVENT"
  | "EMERGENCY_BRIEFING"
  | string;

export type OwningService =
  | "TELEMEDICINE"
  | "FUNDO"
  | "PUBLIC_HEALTH"
  | "CITIZEN_ENGAGEMENT"
  | "ENTERPRISE"
  | "STANDALONE_IMPILO_LIVE"
  | string;

export interface LiveEvent {
  id: string;
  tenantId?: string;
  title: string;
  description?: string | null;
  mode?: LiveMode | null;
  owningService?: OwningService | null;
  owningEntityId?: string | null;
  eventType: string;
  contextType: LiveContextType;
  status: LiveEventStatus;
  facilityId?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  cpdEnabled: boolean;
  cpdPoints?: number | string | null;
  attendanceThresholdMinutes?: number | null;
  chatEnabled?: boolean;
  qnaEnabled?: boolean;
  pollsEnabled?: boolean;
}

export interface LiveRegistration {
  id: string;
  eventId: string;
  participantId: string;
  registrationStatus: string;
  saved: boolean;
  registeredAt?: string;
}

export interface LiveRoomToken {
  roomId: string;
  roomUrl: string;
  accessToken: string;
  provider: string;
  channel: string;
}

export interface LiveRoomSession {
  sessionId: string;
  eventId: string;
  providerType?: string;
  providerRoomId?: string;
  healthStatus?: string;
  startedAt?: string;
  endedAt?: string;
  recordingRef?: string;
  playbackUrlRef?: string;
}

export interface LiveReplayInfo {
  eventId: string;
  status: LiveEventStatus;
  replayAllowed: boolean;
  recordingAllowed: boolean;
  sessionId?: string;
  recordingRef?: string | null;
  playbackUrl?: string | null;
  providerType?: string;
  replayRoomUrl?: string;
  replayAccessToken?: string;
}

export interface LiveMediaHealth {
  providerType: string;
  productionReady: boolean;
  eventStatus: LiveEventStatus;
  recordingAllowed: boolean;
  replayAllowed: boolean;
  healthStatus?: string;
  mediaHealth?: string;
  mediaHealthy?: boolean;
  mediaNote?: string;
}

export interface LiveQuestion {
  id: string;
  eventId: string;
  participantId: string;
  participantType: string;
  questionText: string;
  status: string;
  anonymousAllowed: boolean;
  answeredBy?: string | null;
  answeredAt?: string | null;
  upvotes: number;
  pinned: boolean;
  createdAt?: string;
}

export interface LiveChatMessage {
  id: string;
  eventId: string;
  participantId: string;
  participantType: string;
  message: string;
  status: string;
  moderatedBy?: string | null;
  createdAt?: string;
}

export interface LivePoll {
  id: string;
  eventId: string;
  question: string;
  options: string;
  status: string;
  createdBy: string;
  createdAt?: string;
}

export interface LiveResource {
  id: string;
  eventId: string;
  fileId?: string | null;
  resourceType: string;
  title: string;
  visibility: string;
  createdAt?: string;
}

export interface LiveAttendance {
  id: string;
  eventId: string;
  participantId: string;
  liveWatchMinutes: number;
  totalWatchMinutes: number;
  eligibleForCertificate: boolean;
  eligibleForCpd: boolean;
  completionStatus: string;
}

export interface LiveCertificate {
  id: string;
  eventId: string;
  participantId: string;
  certificateType: string;
  verificationCode: string;
  issuedAt?: string;
}

export interface LiveAnalyticsDashboard {
  eventId: string;
  metrics: Record<string, unknown>;
}

// ============================================================================
// Request payloads
// ============================================================================

export interface CreateLiveEventPayload {
  title: string;
  description?: string;
  mode?: LiveMode;
  owningService?: OwningService;
  owningEntityId?: string;
  eventType: string;
  contextType?: LiveContextType;
  audienceType?: string;
  visibility?: string;
  organiserType?: string;
  organiserId?: string;
  facilityId?: string;
  districtId?: string;
  provinceId?: string;
  programmeId?: string;
  startTime?: string;
  endTime?: string;
  registrationRequired?: boolean;
  maxParticipants?: number;
  recordingAllowed?: boolean;
  replayAllowed?: boolean;
  cpdEnabled?: boolean;
  cpdPoints?: number;
  attendanceThresholdMinutes?: number;
  agenda?: string;
  objectives?: string;
  linkedFundoCourseId?: string;
  linkedMadiDriveId?: string;
}

export interface LiveRegisterPayload {
  participantId: string;
  participantType: string;
  inviteSource?: string;
}

export interface LiveJoinRoomPayload {
  participantId: string;
  participantType: string;
  role?: string;
  consentGranted?: boolean;
}

export interface LiveRoomTokenPayload {
  participantId: string;
  role?: string;
}

export interface LiveQuestionPayload {
  participantId: string;
  participantType: string;
  questionText: string;
  anonymousAllowed?: boolean;
}

export interface LiveChatPayload {
  participantId: string;
  participantType: string;
  message: string;
}

export interface LivePollPayload {
  question: string;
  options: string[];
  createdBy: string;
}

export interface LivePollResponsePayload {
  participantId: string;
  selectedOption: string;
}

export interface LiveModerationPayload {
  action: string;
  moderatedBy: string;
}

export interface LiveResourcePayload {
  title: string;
  resourceType?: string;
  fileId?: string;
  visibility?: string;
}

export type LiveComposerOp =
  | "suggest_title"
  | "suggest_agenda"
  | "suggest_objectives"
  | "moderation_hint"
  | "cpd_summary"
  | "discover_upcoming"
  | "discover_replays"
  | "route_to_session";

export interface LiveComposerAssistPayload {
  op: LiveComposerOp;
  eventTitle?: string;
  eventType?: string;
  context?: string;
  language?: string;
}

export interface LiveComposerAssistResult {
  result: unknown;
  fallbackUsed?: boolean;
}

// ============================================================================
// Client surface
// ============================================================================

const BASE = "/internal/v1/live";

/** Unwrap BFF `{ data, meta }` envelopes when present. */
export function unwrapLiveData<T>(response: unknown): T {
  if (response && typeof response === "object" && "data" in response) {
    return (response as { data: T }).data;
  }
  return response as T;
}

export const liveApi = {
  // Events
  listEvents: async (status?: string) =>
    unwrapLiveData<LiveEvent[]>(
      await apiClient.get(`${BASE}/events${status ? `?status=${encodeURIComponent(status)}` : ""}`),
    ),

  getEvent: async (eventId: string) =>
    unwrapLiveData<LiveEvent>(await apiClient.get(`${BASE}/events/${eventId}`)),

  createEvent: async (body: CreateLiveEventPayload) =>
    unwrapLiveData<LiveEvent>(await apiClient.post(`${BASE}/events`, body)),

  updateEvent: async (eventId: string, body: Partial<CreateLiveEventPayload>) =>
    unwrapLiveData<LiveEvent>(await apiClient.put(`${BASE}/events/${eventId}`, body)),

  scheduleEvent: async (eventId: string) =>
    unwrapLiveData<LiveEvent>(await apiClient.post(`${BASE}/events/${eventId}/schedule`, {})),

  cancelEvent: async (eventId: string) =>
    unwrapLiveData<LiveEvent>(await apiClient.post(`${BASE}/events/${eventId}/cancel`, {})),

  // Discovery
  discoverByContext: async (contextType: LiveContextType) =>
    unwrapLiveData<LiveEvent[]>(
      await apiClient.get(`${BASE}/discover/context/${encodeURIComponent(contextType)}`),
    ),

  discoverByFacility: async (facilityId: string) =>
    unwrapLiveData<LiveEvent[]>(
      await apiClient.get(`${BASE}/discover/facility/${encodeURIComponent(facilityId)}`),
    ),

  discoverByRole: async (userId: string, role: string) =>
    unwrapLiveData<LiveEvent[]>(
      await apiClient.get(
        `${BASE}/discover/role?userId=${encodeURIComponent(userId)}&role=${encodeURIComponent(role)}`,
      ),
    ),

  // Registrations
  register: async (eventId: string, body: LiveRegisterPayload) =>
    unwrapLiveData<LiveRegistration>(
      await apiClient.post(`${BASE}/registrations/events/${eventId}`, body),
    ),

  cancelRegistration: async (eventId: string, participantId: string) =>
    unwrapLiveData<LiveRegistration>(
      await apiClient.delete(`${BASE}/registrations/events/${eventId}/participants/${encodeURIComponent(participantId)}`),
    ),

  saveBookmark: async (eventId: string, participantId: string, saved: boolean) =>
    unwrapLiveData<LiveRegistration>(
      await apiClient.put(
        `${BASE}/registrations/events/${eventId}/participants/${encodeURIComponent(participantId)}/saved?saved=${saved}`,
        {},
      ),
    ),

  listRegistrationsForEvent: async (eventId: string) =>
    unwrapLiveData<LiveRegistration[]>(
      await apiClient.get(`${BASE}/registrations/events/${eventId}`),
    ),

  listRegistrationsForParticipant: async (participantId: string) =>
    unwrapLiveData<LiveRegistration[]>(
      await apiClient.get(`${BASE}/registrations/participants/${encodeURIComponent(participantId)}`),
    ),

  // Room
  joinRoom: async (eventId: string, body: LiveJoinRoomPayload) =>
    unwrapLiveData<LiveRoomSession>(
      await apiClient.post(`${BASE}/room/${eventId}/join`, body),
    ),

  getRoomToken: async (eventId: string, body: LiveRoomTokenPayload) =>
    unwrapLiveData<LiveRoomToken>(
      await apiClient.post(`${BASE}/room/${eventId}/token`, body),
    ),

  startRoom: async (eventId: string) =>
    unwrapLiveData<LiveRoomSession>(await apiClient.post(`${BASE}/room/${eventId}/start`, {})),

  endRoom: async (eventId: string) =>
    unwrapLiveData<LiveRoomSession>(await apiClient.post(`${BASE}/room/${eventId}/end`, {})),

  startRecording: async (eventId: string) =>
    unwrapLiveData<LiveRoomSession>(await apiClient.post(`${BASE}/room/${eventId}/record`, {})),

  getMediaHealth: async (eventId: string) =>
    unwrapLiveData<LiveMediaHealth>(await apiClient.get(`${BASE}/room/${eventId}/media-health`)),

  getReplay: async (eventId: string) =>
    unwrapLiveData<LiveReplayInfo>(await apiClient.get(`${BASE}/room/${eventId}/replay`)),

  processReplay: async (eventId: string) =>
    unwrapLiveData<LiveReplayInfo>(await apiClient.post(`${BASE}/room/${eventId}/process-replay`, {})),

  publishReplay: async (eventId: string) =>
    unwrapLiveData<LiveReplayInfo>(await apiClient.post(`${BASE}/room/${eventId}/publish-replay`, {})),

  // Interactions
  submitQuestion: async (eventId: string, body: LiveQuestionPayload) =>
    unwrapLiveData<LiveQuestion>(
      await apiClient.post(`${BASE}/interactions/${eventId}/questions`, body),
    ),

  listQuestions: async (eventId: string) =>
    unwrapLiveData<LiveQuestion[]>(
      await apiClient.get(`${BASE}/interactions/${eventId}/questions`),
    ),

  upvoteQuestion: async (eventId: string, questionId: string) =>
    unwrapLiveData<LiveQuestion>(
      await apiClient.post(`${BASE}/interactions/${eventId}/questions/${questionId}/upvote`, {}),
    ),

  postChat: async (eventId: string, body: LiveChatPayload) =>
    unwrapLiveData<LiveChatMessage>(
      await apiClient.post(`${BASE}/interactions/${eventId}/chat`, body),
    ),

  listChat: async (eventId: string) =>
    unwrapLiveData<LiveChatMessage[]>(
      await apiClient.get(`${BASE}/interactions/${eventId}/chat`),
    ),

  createPoll: async (eventId: string, body: LivePollPayload) =>
    unwrapLiveData<LivePoll>(
      await apiClient.post(`${BASE}/interactions/${eventId}/polls`, body),
    ),

  activatePoll: async (eventId: string, pollId: string) =>
    unwrapLiveData<LivePoll>(
      await apiClient.post(`${BASE}/interactions/${eventId}/polls/${pollId}/activate`, {}),
    ),

  respondPoll: async (eventId: string, pollId: string, body: LivePollResponsePayload) =>
    unwrapLiveData<Record<string, unknown>>(
      await apiClient.post(`${BASE}/interactions/${eventId}/polls/${pollId}/respond`, body),
    ),

  listPolls: async (eventId: string) =>
    unwrapLiveData<LivePoll[]>(await apiClient.get(`${BASE}/interactions/${eventId}/polls`)),

  listResources: async (eventId: string) =>
    unwrapLiveData<LiveResource[]>(
      await apiClient.get(`${BASE}/interactions/${eventId}/resources`),
    ),

  addResource: async (eventId: string, body: LiveResourcePayload) =>
    unwrapLiveData<LiveResource>(
      await apiClient.post(`${BASE}/interactions/${eventId}/resources`, body),
    ),

  // Moderation
  moderateChat: async (eventId: string, messageId: string, body: LiveModerationPayload) =>
    unwrapLiveData<LiveChatMessage>(
      await apiClient.post(`${BASE}/moderation/${eventId}/chat/${messageId}`, body),
    ),

  moderateQuestion: async (eventId: string, questionId: string, body: LiveModerationPayload) =>
    unwrapLiveData<LiveQuestion>(
      await apiClient.post(`${BASE}/moderation/${eventId}/questions/${questionId}`, body),
    ),

  setChatEnabled: async (eventId: string, enabled: boolean) =>
    unwrapLiveData<LiveEvent>(
      await apiClient.put(`${BASE}/moderation/${eventId}/chat-enabled`, { enabled }),
    ),

  setQnaEnabled: async (eventId: string, enabled: boolean) =>
    unwrapLiveData<LiveEvent>(
      await apiClient.put(`${BASE}/moderation/${eventId}/qna-enabled`, { enabled }),
    ),

  // Attendance
  attendanceJoin: async (eventId: string, participantId: string, participantType: string) =>
    unwrapLiveData<LiveAttendance>(
      await apiClient.post(`${BASE}/attendance/${eventId}/join`, { participantId, participantType }),
    ),

  attendanceLeave: async (eventId: string, participantId: string) =>
    unwrapLiveData<LiveAttendance>(
      await apiClient.post(`${BASE}/attendance/${eventId}/leave`, { participantId }),
    ),

  trackAttendanceMinutes: async (
    eventId: string,
    participantId: string,
    liveMinutes: number,
    replayMinutes: number,
  ) =>
    unwrapLiveData<LiveAttendance>(
      await apiClient.put(`${BASE}/attendance/${eventId}/participants/${encodeURIComponent(participantId)}/minutes`, {
        liveMinutes,
        replayMinutes,
      }),
    ),

  listAttendance: async (eventId: string) =>
    unwrapLiveData<LiveAttendance[]>(await apiClient.get(`${BASE}/attendance/${eventId}`)),

  getAttendance: async (eventId: string, participantId: string) =>
    unwrapLiveData<LiveAttendance>(
      await apiClient.get(
        `${BASE}/attendance/${eventId}/participants/${encodeURIComponent(participantId)}`,
      ),
    ),

  // Certificates
  issueAttendanceCertificate: async (eventId: string, participantId: string) =>
    unwrapLiveData<LiveCertificate>(
      await apiClient.post(`${BASE}/certificates/${eventId}/attendance/${encodeURIComponent(participantId)}`, {}),
    ),

  issueCpdCertificate: async (eventId: string, participantId: string) =>
    unwrapLiveData<LiveCertificate>(
      await apiClient.post(`${BASE}/certificates/${eventId}/cpd/${encodeURIComponent(participantId)}`, {}),
    ),

  listCertificates: async (eventId: string) =>
    unwrapLiveData<LiveCertificate[]>(await apiClient.get(`${BASE}/certificates/${eventId}`)),

  verifyCertificate: async (verificationCode: string) =>
    unwrapLiveData<LiveCertificate>(
      await apiClient.get(`${BASE}/certificates/verify/${encodeURIComponent(verificationCode)}`),
    ),

  // Analytics
  captureAnalyticsSnapshot: async (eventId: string) =>
    apiClient.post(`${BASE}/analytics/${eventId}/snapshot`, {}),

  getAnalyticsDashboard: async (eventId: string) =>
    unwrapLiveData<LiveAnalyticsDashboard>(
      await apiClient.get(`${BASE}/analytics/${eventId}/dashboard`),
    ),

  getAnalyticsHistory: async (eventId: string, metricName: string) =>
    unwrapLiveData<Array<Record<string, unknown>>>(
      await apiClient.get(
        `${BASE}/analytics/${eventId}/history?metricName=${encodeURIComponent(metricName)}`,
      ),
    ),

  // Typed scheduling (owning-service bridges)
  scheduleClinicalSession: async (body: Record<string, unknown>) =>
    unwrapLiveData<Record<string, unknown>>(
      await apiClient.post(`${BASE}/clinical-sessions`, body),
    ),

  scheduleFundoWebinar: async (body: Record<string, unknown>) =>
    unwrapLiveData<Record<string, unknown>>(
      await apiClient.post(`${BASE}/fundo-webinars`, body),
    ),

  schedulePublicBroadcast: async (body: Record<string, unknown>) =>
    unwrapLiveData<Record<string, unknown>>(
      await apiClient.post(`${BASE}/public-broadcasts`, body),
    ),

  // Nompilo composer assist
  composerAssist: async (body: LiveComposerAssistPayload): Promise<LiveComposerAssistResult> => {
    try {
      const res = await apiClient.post<ApiResponse<unknown>>(`${BASE}/composer/assist`, body);
      return { result: res.data, fallbackUsed: false };
    } catch {
      if (body.op === "suggest_title" && body.eventType) {
        return {
          result: `${body.eventType.replace(/_/g, " ")} — ${body.context ?? "community health session"}`,
          fallbackUsed: true,
        };
      }
      if (body.op === "suggest_agenda") {
        return {
          result: ["Welcome and objectives", "Expert presentation", "Q&A and polls", "Summary and next steps"],
          fallbackUsed: true,
        };
      }
      if (body.op === "suggest_objectives") {
        return {
          result: [
            "Understand key health messages",
            "Identify actionable steps for participants",
            "Capture questions for follow-up care pathways",
          ],
          fallbackUsed: true,
        };
      }
      if (body.op === "moderation_hint") {
        return { result: { flagged: false, guidance: "Review chat for clinical misinformation." }, fallbackUsed: true };
      }
      if (body.op === "discover_upcoming") {
        return {
          result: { paths: ["/live", "/live/my-events", "/live/cpd"], hint: "Upcoming sessions by role and registration." },
          fallbackUsed: true,
        };
      }
      if (body.op === "discover_replays") {
        return {
          result: { paths: ["/live/replays", "/live/discover"], hint: "Governed replay library and public health talks." },
          fallbackUsed: true,
        };
      }
      if (body.op === "route_to_session") {
        return {
          result: { pathPrefix: "/live/event/", hint: "Join from event detail when consent and role allow." },
          fallbackUsed: true,
        };
      }
      return { result: body.context ?? "", fallbackUsed: true };
    }
  },
};
