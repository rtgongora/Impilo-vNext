/**
 * Impilo Live contract types — mirror `contracts/openapi/impilo-live.openapi.yaml`.
 * Source of truth for cross-service Live mode/ownership doctrine.
 */

export type LiveMode =
  | "CLINICAL_SESSION"
  | "PROFESSIONAL_MEETING"
  | "WEBINAR_CPD"
  | "PUBLIC_BROADCAST"
  | "HYBRID_EVENT"
  | "EMERGENCY_BRIEFING";

export type OwningService =
  | "TELEMEDICINE"
  | "FUNDO"
  | "PUBLIC_HEALTH"
  | "CITIZEN_ENGAGEMENT"
  | "ENTERPRISE"
  | "STANDALONE_IMPILO_LIVE";

export type LiveEventStatus =
  | "DRAFT"
  | "SCHEDULED"
  | "LIVE"
  | "ENDED"
  | "PROCESSING_REPLAY"
  | "PUBLISHED_REPLAY"
  | "CANCELLED";

export interface LiveEvent {
  id: string;
  title: string;
  mode?: LiveMode | null;
  owningService?: OwningService | null;
  owningEntityId?: string | null;
  eventType?: string;
  contextType?: string;
  status?: LiveEventStatus | string;
  startTime?: string;
  endTime?: string;
  cpdEnabled?: boolean;
}

export interface ScheduleClinicalLiveSessionRequest {
  telehealthSessionId: string;
  encounterId?: string;
  patientHealthId: string;
  providerId: string;
  facilityId?: string;
  title?: string;
  startTime?: string;
  endTime?: string;
  consentGranted: boolean;
  recordingAllowed?: boolean;
}

export interface ScheduleFundoWebinarRequest {
  courseId: string;
  title: string;
  description?: string;
  startTime?: string;
  endTime?: string;
  trainerIds?: string[];
  learnerAudience?: string;
  cpdEnabled?: boolean;
  cpdPoints?: number;
  attendanceThresholdMinutes?: number;
  replayAllowed?: boolean;
  visibility?: string;
}

export interface SchedulePublicBroadcastRequest {
  linkedCampaignId: string;
  title: string;
  description?: string;
  startTime?: string;
  endTime?: string;
  moderatorIds?: string[];
  verifiedSpeakerIds?: string[];
  emergencyBriefing?: boolean;
  visibility?: string;
}

export interface LiveGovernanceError {
  code: string;
  message: string;
  mode?: LiveMode;
  owningService?: OwningService;
}

/** Nompilo composer assist operations for live session discovery/routing. */
export type NompiloLiveComposerOperation =
  | "discover_upcoming"
  | "discover_replays"
  | "route_to_session";

export interface NompiloLiveComposerAssistRequest {
  operation: NompiloLiveComposerOperation;
  query?: string;
  eventId?: string;
  mode?: LiveMode;
}

export interface NompiloLiveComposerAssistResponse {
  operation: NompiloLiveComposerOperation;
  events?: LiveEvent[];
  routePath?: string;
  summary?: string;
}

/* ── Adaptive Session Suite: session-mode engine ─────────────────────────────
 * SessionMode is the cross-service key for the adaptive real-time suite;
 * LiveMode remains live-service's internal refinement. The JSON documents in
 * contracts/schemas/session-templates/ are the source of truth — these types
 * mirror session-template.schema.json for web/mobile consumers.
 */

export type SessionMode =
  | "MEETING"
  | "LIVE_EVENT"
  | "LEARNING_LIVE"
  | "LEARNING_RECORDING"
  | "TELEMEDICINE";

export type SessionOwningService = "KHULUMA" | "LIVE" | "FUNDO" | "PCT";

export type SessionLayout = "grid" | "speaker" | "stage" | "classroom" | "consult" | "player";

export type SessionJoinFlow = "DIRECT" | "LOBBY" | "REGISTRATION" | "SCHEDULED_CHECKIN";

export type SessionLobbyBehaviour = "NONE" | "WAITING_ROOM" | "KNOCK";

export interface SessionTokenGrantProfile {
  canPublish: boolean;
  canSubscribe: boolean;
  canPublishData: boolean;
  roomAdmin?: boolean;
  hidden?: boolean;
  audioOnlyDefault?: boolean;
}

export interface SessionTemplate {
  sessionMode: SessionMode;
  owningService: SessionOwningService;
  liveModes: LiveMode[];
  roles: Record<string, SessionTokenGrantProfile>;
  defaultLayout: SessionLayout;
  joinFlow: SessionJoinFlow;
  lobbyBehaviour: SessionLobbyBehaviour;
  tokenTtlSeconds: number;
  maxParticipants: number;
  roomPrefix?: string;
  recording: {
    defaultOn: boolean;
    whoCanStart: string[];
    consentRequired: boolean;
    sensitivityClass?: "GENERAL" | "PROFESSIONAL" | "CLINICAL";
    artifactOwner: SessionOwningService;
  };
  chat: { enabled: boolean; moderated: boolean; persistence?: "NONE" | "SESSION" | "CONVERSATION" };
  moderationRequired: boolean;
  screenShareRoles: string[];
  resourceSharing: boolean;
  auditDepth: "BASIC" | "FULL" | "CLINICAL";
  telemetry: { participantStats: boolean; qualityEvents?: boolean };
  postSessionActions: string[];
  khulumaNotificationKeys: string[];
  mobileParity: "FULL" | "JOIN_CAPABLE" | "VIEW_ONLY" | "NONE";
  fallbackRules: { audioFirstAllowed: boolean; reconnectGraceSeconds: number; sipFallback?: boolean };
  completionCriteriaRef?: string | null;
}

/** SessionMode ↔ LiveMode doctrine mapping (mirrors the template documents). */
export const SESSION_MODE_LIVE_MODES: Record<SessionMode, LiveMode[]> = {
  MEETING: ["PROFESSIONAL_MEETING"],
  LIVE_EVENT: ["PUBLIC_BROADCAST", "HYBRID_EVENT", "EMERGENCY_BRIEFING"],
  LEARNING_LIVE: ["WEBINAR_CPD"],
  LEARNING_RECORDING: [],
  TELEMEDICINE: ["CLINICAL_SESSION"],
};
