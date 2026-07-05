/**
 * Learning live-session helpers — pure resolution logic shared by the
 * /learning/sessions surfaces (list, detail, classroom).
 *
 * The W3 backend adds first-class `sessionMode` ("LIVE" | "RECORDED" |
 * "HYBRID" | "IN_PERSON"), `liveEventId` and `joinPath` to the session
 * payloads. Sessions created before that wave only carry the legacy
 * `sessionType` free text plus `metadata.impiloLiveEventId` /
 * `metadata.impiloLiveJoinPath`, so every resolver here prefers the
 * first-class field and falls back to the legacy shape.
 */

export type LearningSessionMode = "LIVE" | "RECORDED" | "HYBRID" | "IN_PERSON";

export type LearningSessionRecord = Record<string, unknown> & {
  id?: string;
  courseId?: string;
  cohortId?: string | null;
  title?: string;
  sessionType?: string;
  sessionMode?: string;
  liveEventId?: string | null;
  joinPath?: string | null;
  startsAt?: string;
  endsAt?: string | null;
  facilitator?: string | null;
  facilitatorId?: string | null;
  venueId?: string | null;
  locationRef?: string | null;
  facilitators?: Array<Record<string, unknown>>;
  metadata?: Record<string, unknown> | null;
};

function asString(value: unknown): string | undefined {
  if (typeof value !== "string") return undefined;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

function metadataOf(session: LearningSessionRecord): Record<string, unknown> {
  return session.metadata && typeof session.metadata === "object"
    ? (session.metadata as Record<string, unknown>)
    : {};
}

/** Live event id: first-class `liveEventId`, else legacy `metadata.impiloLiveEventId`. */
export function resolveLiveEventId(session: LearningSessionRecord): string | undefined {
  return asString(session.liveEventId) ?? asString(metadataOf(session).impiloLiveEventId);
}

/** In-shell join path: first-class `joinPath`, else legacy `metadata.impiloLiveJoinPath`. */
export function resolveJoinPath(session: LearningSessionRecord): string | undefined {
  return asString(session.joinPath) ?? asString(metadataOf(session).impiloLiveJoinPath);
}

/**
 * Session mode: prefer the first-class `sessionMode`; otherwise derive from
 * the legacy `sessionType` text (mirrors the learning-service Impilo Live
 * scheduling heuristic) plus the presence of a live event.
 */
export function resolveSessionMode(session: LearningSessionRecord): LearningSessionMode | undefined {
  const declared = asString(session.sessionMode)?.toUpperCase();
  if (declared === "LIVE" || declared === "RECORDED" || declared === "HYBRID" || declared === "IN_PERSON") {
    return declared;
  }
  const type = asString(session.sessionType)?.toUpperCase() ?? "";
  if (type.includes("HYBRID")) return "HYBRID";
  if (type.includes("VIRTUAL") || type.includes("LIVE") || type.includes("WEBINAR")) return "LIVE";
  if (type.includes("RECORD") || type.includes("SELF_PACED") || type.includes("ASYNC")) return "RECORDED";
  if (
    type.includes("IN_PERSON") ||
    type.includes("PHYSICAL") ||
    type.includes("CLASSROOM") ||
    type.includes("WORKSHOP") ||
    type.includes("PRACTICAL")
  ) {
    return "IN_PERSON";
  }
  // Unknown legacy type: a linked live event is the strongest remaining signal.
  return resolveLiveEventId(session) ? "LIVE" : undefined;
}

/** A session the classroom can actually join: live-capable mode + linked live event. */
export function isJoinableLiveSession(session: LearningSessionRecord): boolean {
  const mode = resolveSessionMode(session);
  return (mode === "LIVE" || mode === "HYBRID") && Boolean(resolveLiveEventId(session));
}

/** Upcoming = not yet ended (falls back to startsAt when there is no endsAt). */
export function isUpcomingSession(session: LearningSessionRecord, now: Date = new Date()): boolean {
  const boundary = asString(session.endsAt) ?? asString(session.startsAt);
  if (!boundary) return true;
  const parsed = new Date(boundary);
  return Number.isNaN(parsed.getTime()) ? true : parsed.getTime() >= now.getTime();
}

export type ClassroomRole = "facilitator" | "learner";

export interface ClassroomIdentity {
  /** Learning subject id (PROVIDER_PUBLIC_ID / USER_HEALTH_ID resolution). */
  subjectId?: string;
  /** Activated provider id from the auth store, when present. */
  providerId?: string;
}

/**
 * Facilitator detection: the current user is a facilitator when any of their
 * identifiers matches (a) the structured `facilitators[]` list on the W3
 * detail payload, (b) the facilitator record referenced by `facilitatorId`,
 * or (c) the legacy free-text `facilitator` column (which holds a trainer id).
 */
export function resolveClassroomRole(
  session: LearningSessionRecord,
  identity: ClassroomIdentity,
  facilitatorDirectory: Array<Record<string, unknown>> = []
): ClassroomRole {
  const ids = [identity.subjectId, identity.providerId]
    .map((v) => asString(v))
    .filter((v): v is string => Boolean(v));
  if (ids.length === 0) return "learner";

  const candidates = new Set<string>();
  const legacy = asString(session.facilitator);
  if (legacy) candidates.add(legacy);

  const structured = Array.isArray(session.facilitators) ? session.facilitators : [];
  for (const f of structured) {
    for (const key of ["subjectId", "subject_id", "providerId", "id"]) {
      const v = asString((f as Record<string, unknown>)[key]);
      if (v) candidates.add(v);
    }
  }

  const facilitatorId = asString(session.facilitatorId);
  if (facilitatorId) {
    candidates.add(facilitatorId);
    const record = facilitatorDirectory.find((f) => asString(f.id) === facilitatorId);
    if (record) {
      const v = asString(record.subjectId) ?? asString(record.subject_id);
      if (v) candidates.add(v);
    }
  }
  // The user's own facilitator registration (directory match by subject).
  for (const record of facilitatorDirectory) {
    const subject = asString(record.subjectId) ?? asString(record.subject_id);
    if (subject && ids.includes(subject) && facilitatorId && asString(record.id) === facilitatorId) {
      return "facilitator";
    }
  }

  return ids.some((id) => candidates.has(id)) ? "facilitator" : "learner";
}
