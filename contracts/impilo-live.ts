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
