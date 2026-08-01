/**
 * Event Normalizers — Convert heterogeneous backend events into unified TimelineEvents.
 *
 * Each normalizer handles a specific resource type from the backend.
 * Register custom normalizers for app-specific event types.
 */

import type { RawBackendEvent, TimelineEvent, TimelineEventType, EventNormalizer } from "./types";

const normalizerRegistry = new Map<string, EventNormalizer>();

/**
 * Register a custom normalizer for a resource type.
 */
export function registerNormalizer(resourceType: string, normalizer: EventNormalizer): void {
  normalizerRegistry.set(resourceType.toLowerCase(), normalizer);
}

/**
 * Normalize a raw backend event into a TimelineEvent.
 * Returns null if no normalizer is registered for the resource type.
 */
export function normalizeEvent(raw: RawBackendEvent): TimelineEvent | null {
  const normalizer = normalizerRegistry.get(raw.resourceType.toLowerCase());
  if (normalizer) {
    return normalizer(raw);
  }
  return defaultNormalizer(raw);
}

/**
 * Normalize a batch of raw events, filtering out nulls and sorting by timestamp descending.
 */
export function normalizeEvents(rawEvents: RawBackendEvent[]): TimelineEvent[] {
  return rawEvents
    .map(normalizeEvent)
    .filter((e): e is TimelineEvent => e !== null)
    .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
}

// --- Built-in normalizers ---

function defaultNormalizer(raw: RawBackendEvent): TimelineEvent {
  const deepLink = raw.data.deepLink as string | undefined;
  const sourceId = (raw.data.sourceId as string | undefined) ?? raw.id;
  return {
    id: raw.id,
    type: mapResourceType(raw.resourceType),
    timestamp: raw.timestamp,
    title: (raw.data.title as string) ?? raw.resourceType,
    summary: (raw.data.summary as string) ?? (raw.data.description as string) ?? "",
    facilityId: raw.data.facilityId as string | undefined,
    facilityName: raw.data.facilityName as string | undefined,
    actorId: raw.data.actorId as string | undefined,
    actorName: raw.data.actorName as string | undefined,
    deepLink,
    metadata: raw.data,
    sourceSystem: raw.source,
    sourceId,
    status: raw.data.status as string | undefined,
    provenance: { system: raw.source, resourceType: raw.resourceType, resourceId: sourceId },
    visibility: (raw.data.visibility as TimelineEvent["visibility"] | undefined) ?? "SELF",
    sensitivity: (raw.data.sensitivity as TimelineEvent["sensitivity"] | undefined) ?? "STANDARD",
    actions: deepLink ? [{ id: "open", label: "View details", deepLink, enabled: true }] : [],
  };
}

function mapResourceType(resourceType: string): TimelineEventType {
  const mapping: Record<string, TimelineEventType> = {
    encounter: "VISIT",
    visit: "VISIT",
    observation: "VITALS",
    vitals: "VITALS",
    condition: "DIAGNOSIS",
    diagnosis: "DIAGNOSIS",
    medicationrequest: "PRESCRIPTION",
    prescription: "PRESCRIPTION",
    medicationdispense: "DISPENSING",
    dispensing: "DISPENSING",
    servicerequest: "LAB_ORDER",
    lab_order: "LAB_ORDER",
    diagnosticreport: "LAB_RESULT",
    lab_result: "LAB_RESULT",
    imagingstudy: "IMAGING",
    imaging: "IMAGING",
    referral: "REFERRAL",
    appointment: "APPOINTMENT",
    admission: "ADMISSION",
    discharge: "DISCHARGE",
    communication: "MESSAGE",
    message: "MESSAGE",
    documentreference: "DOCUMENT",
    document: "DOCUMENT",
    consent: "CONSENT",
    coverage: "COVERAGE",
    outreach: "OUTREACH",
    immunization: "IMMUNIZATION",
    vaccination: "IMMUNIZATION",
    wellness: "WELLNESS",
    feedback: "FEEDBACK",
    telecare: "TELECARE",
    teleconsultation: "TELECARE",
    collection: "COLLECTION",
    care_milestone: "CARE_MILESTONE",
    journey_opened: "CARE_MILESTONE",
  };
  return mapping[resourceType.toLowerCase()] ?? "SYSTEM";
}

// Register built-in normalizers
registerNormalizer("Encounter", (raw) => ({
  id: raw.id,
  type: "VISIT",
  timestamp: raw.timestamp,
  title: `Visit — ${(raw.data.type as string) ?? "General"}`,
  summary: (raw.data.reasonText as string) ?? "Clinical encounter",
  facilityId: raw.data.facilityId as string | undefined,
  facilityName: raw.data.facilityName as string | undefined,
  actorId: raw.data.practitionerId as string | undefined,
  actorName: raw.data.practitionerName as string | undefined,
  deepLink: `/visits/${raw.id}`,
  metadata: raw.data,
  sourceSystem: raw.source,
  sourceId: raw.id,
  status: raw.data.status as string | undefined,
  provenance: { system: raw.source, resourceType: raw.resourceType, resourceId: raw.id },
  visibility: "SELF",
  sensitivity: "STANDARD",
  actions: [{ id: "open", label: "View visit", deepLink: `/visits/${raw.id}`, enabled: true }],
}));

registerNormalizer("Observation", (raw) => ({
  id: raw.id,
  type: "VITALS",
  timestamp: raw.timestamp,
  title: `Vitals — ${(raw.data.code as string) ?? "Observation"}`,
  summary: `${raw.data.value ?? ""}${raw.data.unit ? ` ${raw.data.unit}` : ""}`,
  facilityId: raw.data.facilityId as string | undefined,
  facilityName: raw.data.facilityName as string | undefined,
  actorId: raw.data.performerId as string | undefined,
  actorName: raw.data.performerName as string | undefined,
  deepLink: `/vitals/${raw.id}`,
  metadata: raw.data,
  sourceSystem: raw.source,
  sourceId: raw.id,
  provenance: { system: raw.source, resourceType: raw.resourceType, resourceId: raw.id },
  visibility: "SELF",
  sensitivity: "STANDARD",
  actions: [{ id: "open", label: "View observation", deepLink: `/vitals/${raw.id}`, enabled: true }],
}));

registerNormalizer("MedicationRequest", (raw) => ({
  id: raw.id,
  type: "PRESCRIPTION",
  timestamp: raw.timestamp,
  title: `Rx — ${(raw.data.medicationName as string) ?? "Prescription"}`,
  summary: (raw.data.dosageInstruction as string) ?? "",
  facilityId: raw.data.facilityId as string | undefined,
  facilityName: raw.data.facilityName as string | undefined,
  actorId: raw.data.prescriberId as string | undefined,
  actorName: raw.data.prescriberName as string | undefined,
  deepLink: `/prescriptions/${raw.id}`,
  metadata: raw.data,
  sourceSystem: raw.source,
  sourceId: raw.id,
  provenance: { system: raw.source, resourceType: raw.resourceType, resourceId: raw.id },
  visibility: "SELF",
  sensitivity: "STANDARD",
  actions: [{ id: "open", label: "View prescription", deepLink: `/prescriptions/${raw.id}`, enabled: true }],
}));

registerNormalizer("DiagnosticReport", (raw) => ({
  id: raw.id,
  type: "LAB_RESULT",
  timestamp: raw.timestamp,
  title: `Lab Result — ${(raw.data.testName as string) ?? "Diagnostic Report"}`,
  summary: (raw.data.conclusion as string) ?? (raw.data.status as string) ?? "",
  facilityId: raw.data.facilityId as string | undefined,
  facilityName: raw.data.facilityName as string | undefined,
  actorId: raw.data.issuerId as string | undefined,
  actorName: raw.data.issuerName as string | undefined,
  deepLink: `/results/${raw.id}`,
  metadata: raw.data,
  sourceSystem: raw.source,
  sourceId: raw.id,
  status: raw.data.status as string | undefined,
  provenance: { system: raw.source, resourceType: raw.resourceType, resourceId: raw.id },
  visibility: "SELF",
  sensitivity: "SENSITIVE",
  actions: [{ id: "open", label: "View result", deepLink: `/results/${raw.id}`, enabled: true }],
}));

registerNormalizer("ServiceRequest", (raw) => ({
  id: raw.id,
  type: "LAB_ORDER",
  timestamp: raw.timestamp,
  title: `Lab Order — ${(raw.data.testName as string) ?? "Service Request"}`,
  summary: (raw.data.priority as string) ?? "",
  facilityId: raw.data.facilityId as string | undefined,
  facilityName: raw.data.facilityName as string | undefined,
  actorId: raw.data.requesterId as string | undefined,
  actorName: raw.data.requesterName as string | undefined,
  deepLink: `/orders/${raw.id}`,
  metadata: raw.data,
  sourceSystem: raw.source,
  sourceId: raw.id,
  status: raw.data.status as string | undefined,
  provenance: { system: raw.source, resourceType: raw.resourceType, resourceId: raw.id },
  visibility: "SELF",
  sensitivity: "STANDARD",
  actions: [{ id: "open", label: "View order", deepLink: `/orders/${raw.id}`, enabled: true }],
}));
