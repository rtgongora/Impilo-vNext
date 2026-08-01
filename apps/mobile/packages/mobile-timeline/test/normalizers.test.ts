import { describe, it, expect } from "vitest";
import { normalizeEvent, normalizeEvents, registerNormalizer } from "../src/normalizers";
import type { RawBackendEvent } from "../src/types";

describe("normalizeEvent", () => {
  it("normalizes an Encounter to a VISIT timeline event", () => {
    const raw: RawBackendEvent = {
      id: "enc-1",
      resourceType: "Encounter",
      timestamp: "2026-03-15T10:00:00Z",
      data: { type: "General", reasonText: "Follow-up visit", practitionerName: "Dr. Smith" },
      source: "pct-service",
    };
    const event = normalizeEvent(raw);
    expect(event).not.toBeNull();
    expect(event!.type).toBe("VISIT");
    expect(event!.title).toContain("Visit");
    expect(event!.summary).toBe("Follow-up visit");
    expect(event!.actorName).toBe("Dr. Smith");
    expect(event!.deepLink).toBe("/visits/enc-1");
    expect(event!.provenance).toEqual({
      system: "pct-service",
      resourceType: "Encounter",
      resourceId: "enc-1",
    });
    expect(event!.visibility).toBe("SELF");
    expect(event!.actions[0]).toMatchObject({ enabled: true, label: "View visit" });
  });

  it("normalizes an Observation to a VITALS timeline event", () => {
    const raw: RawBackendEvent = {
      id: "obs-1",
      resourceType: "Observation",
      timestamp: "2026-03-15T10:05:00Z",
      data: { code: "Blood Pressure", value: "120/80", unit: "mmHg" },
      source: "pct-service",
    };
    const event = normalizeEvent(raw);
    expect(event!.type).toBe("VITALS");
    expect(event!.summary).toContain("120/80");
  });

  it("normalizes a MedicationRequest to a PRESCRIPTION event", () => {
    const raw: RawBackendEvent = {
      id: "rx-1",
      resourceType: "MedicationRequest",
      timestamp: "2026-03-15T10:10:00Z",
      data: { medicationName: "Metformin 500mg", dosageInstruction: "1 tab twice daily" },
      source: "pharmacy-service",
    };
    const event = normalizeEvent(raw);
    expect(event!.type).toBe("PRESCRIPTION");
    expect(event!.title).toContain("Metformin");
    expect(event!.deepLink).toBe("/prescriptions/rx-1");
  });

  it("normalizes a DiagnosticReport to a LAB_RESULT event", () => {
    const raw: RawBackendEvent = {
      id: "dr-1",
      resourceType: "DiagnosticReport",
      timestamp: "2026-03-15T11:00:00Z",
      data: { testName: "CBC", conclusion: "Normal" },
      source: "oros-service",
    };
    const event = normalizeEvent(raw);
    expect(event!.type).toBe("LAB_RESULT");
    expect(event!.summary).toBe("Normal");
  });

  it("uses default normalizer for unknown resource types", () => {
    const raw: RawBackendEvent = {
      id: "unk-1",
      resourceType: "UnknownType",
      timestamp: "2026-03-15T12:00:00Z",
      data: { title: "Unknown Event", summary: "Something happened" },
      source: "mystery-service",
    };
    const event = normalizeEvent(raw);
    expect(event).not.toBeNull();
    expect(event!.type).toBe("SYSTEM");
    expect(event!.title).toBe("Unknown Event");
    expect(event!.actions).toEqual([]);
  });

  it("maps recorded journey and vaccination events into canonical categories", () => {
    const events = normalizeEvents([
      { id: "j-1", resourceType: "JOURNEY_OPENED", timestamp: "2026-03-15T12:00:00Z", data: {}, source: "pct_journey" },
      { id: "i-1", resourceType: "Immunization", timestamp: "2026-03-15T11:00:00Z", data: {}, source: "fhir" },
    ]);
    expect(events.map((event) => event.type)).toEqual(["CARE_MILESTONE", "IMMUNIZATION"]);
    expect(events[0].provenance.system).toBe("pct_journey");
  });
});

describe("normalizeEvents", () => {
  it("sorts events by timestamp descending", () => {
    const events: RawBackendEvent[] = [
      { id: "a", resourceType: "Encounter", timestamp: "2026-03-15T08:00:00Z", data: {}, source: "pct" },
      { id: "b", resourceType: "Encounter", timestamp: "2026-03-15T12:00:00Z", data: {}, source: "pct" },
      { id: "c", resourceType: "Encounter", timestamp: "2026-03-15T10:00:00Z", data: {}, source: "pct" },
    ];
    const normalized = normalizeEvents(events);
    expect(normalized[0].id).toBe("b");
    expect(normalized[1].id).toBe("c");
    expect(normalized[2].id).toBe("a");
  });
});

describe("registerNormalizer", () => {
  it("allows custom normalizers for resource types", () => {
    registerNormalizer("CustomResource", (raw) => ({
      id: raw.id,
      type: "SYSTEM",
      timestamp: raw.timestamp,
      title: "Custom: " + (raw.data.name as string ?? "unknown"),
      summary: "Custom event",
      metadata: raw.data,
      sourceSystem: raw.source,
      sourceId: raw.id,
      provenance: { system: raw.source, resourceType: raw.resourceType, resourceId: raw.id },
      visibility: "SELF",
      sensitivity: "STANDARD",
      actions: [],
    }));

    const raw: RawBackendEvent = {
      id: "custom-1",
      resourceType: "CustomResource",
      timestamp: "2026-03-15T14:00:00Z",
      data: { name: "Test" },
      source: "custom-service",
    };
    const event = normalizeEvent(raw);
    expect(event!.title).toBe("Custom: Test");
  });
});
