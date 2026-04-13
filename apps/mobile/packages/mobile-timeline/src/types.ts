/**
 * Timeline Types — Unified event model for heterogeneous backend events.
 */

export interface TimelineEvent {
  id: string;
  type: TimelineEventType;
  timestamp: string;
  /**
   * Back-compat aliases used by older screens.
   */
  occurredAt?: string;
  subjectName?: string;
  title: string;
  summary: string;
  facilityId?: string;
  facilityName?: string;
  actorId?: string;
  actorName?: string;
  deepLink?: string;
  metadata: Record<string, unknown>;
  sourceSystem: string;
  sourceId: string;
}

export type TimelineEventType =
  | "VISIT"
  | "VITALS"
  | "DIAGNOSIS"
  | "PRESCRIPTION"
  | "DISPENSING"
  | "LAB_ORDER"
  | "LAB_RESULT"
  | "IMAGING"
  | "REFERRAL"
  | "APPOINTMENT"
  | "ADMISSION"
  | "DISCHARGE"
  | "MESSAGE"
  | "NOTIFICATION"
  | "DOCUMENT"
  | "CONSENT"
  | "COVERAGE"
  | "OUTREACH"
  | "SYSTEM";

export interface TimelineFilters {
  types?: TimelineEventType[];
  startDate?: string;
  endDate?: string;
  facilityId?: string;
  sourceSystem?: string;
}

export interface TimelineResponse {
  items: TimelineEvent[];
  cursor?: string;
  hasMore: boolean;
  totalElements?: number;
}

/**
 * Raw backend event shape before normalization.
 */
export interface RawBackendEvent {
  id: string;
  resourceType: string;
  timestamp: string;
  data: Record<string, unknown>;
  source: string;
}

/**
 * Normalizer function type — converts a raw backend event to a TimelineEvent.
 */
export type EventNormalizer = (raw: RawBackendEvent) => TimelineEvent | null;
