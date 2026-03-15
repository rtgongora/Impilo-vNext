/**
 * @impilo/mobile-timeline — Timeline / Feed SDK
 *
 * Unified event feed that renders heterogeneous events
 * (visits, messages, Rx, labs, referrals) in chronological order.
 * All 4 mobile apps MUST use this package for timeline/feed.
 */

// Types
export type {
  TimelineEvent,
  TimelineEventType,
  TimelineFilters,
  TimelineResponse,
  RawBackendEvent,
  EventNormalizer,
} from "./types";

// Normalizers
export { registerNormalizer, normalizeEvent, normalizeEvents } from "./normalizers";

// Service
export { fetchTimeline, fetchMyTimeline } from "./timelineService";

// React hooks
export { useTimeline, useMyTimeline } from "./hooks";
export type { UseTimelineResult } from "./hooks";
