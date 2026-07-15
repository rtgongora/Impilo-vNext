/**
 * Prehospital ePCR service tests — live capture, envelope unwrap, and offline-queued observations.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mockGet = vi.fn();
const mockPost = vi.fn();
const mockQueueOnRetryable = vi.fn();

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: { get: (...a: unknown[]) => mockGet(...a), post: (...a: unknown[]) => mockPost(...a) },
}));

vi.mock("./offlineClinicalQueue", () => ({
  queueClinicalCreateOnRetryableError: (...a: unknown[]) => mockQueueOnRetryable(...a),
}));

describe("epcrService", () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPost.mockReset();
    mockQueueOnRetryable.mockReset();
  });

  it("getMissionForIncident hits the EMS mission-by-incident endpoint and unwraps the entity", async () => {
    mockGet.mockResolvedValue({ data: { id: "m-1", state: "DISPATCHED", traumaEpisodeId: "ep-1" } });
    const { getMissionForIncident } = await import("./epcrService");

    const m = await getMissionForIncident("inc-1");

    expect(m.id).toBe("m-1");
    expect(m.state).toBe("DISPATCHED");
    expect(mockGet).toHaveBeenCalledWith("/internal/v1/daidzai/ems/incidents/inc-1/mission");
  });

  it("advanceMission posts the target state to the advance endpoint", async () => {
    mockPost.mockResolvedValue({ data: { id: "m-1", state: "ON_SCENE" } });
    const { advanceMission } = await import("./epcrService");

    const m = await advanceMission("m-1", "ON_SCENE");

    expect(m.state).toBe("ON_SCENE");
    expect(mockPost).toHaveBeenCalledWith("/internal/v1/daidzai/ems/missions/m-1/advance", {
      toState: "ON_SCENE",
    });
  });

  it("recordObs sends a live observation and reports it not queued", async () => {
    mockPost.mockResolvedValue({ data: { id: "obs-1" } });
    const { recordObs } = await import("./epcrService");

    const res = await recordObs({ missionId: "m-1", eventType: "VITALS", payload: { hr: 120, sbp: 90 } });

    expect(res.queued).toBe(false);
    expect(res.id).toBe("obs-1");
    expect(mockPost).toHaveBeenCalledWith("/internal/v1/daidzai/ems/missions/m-1/epcr/events", {
      eventType: "VITALS",
      channel: "OBS",
      payload: { hr: 120, sbp: 90 },
    });
  });

  it("recordObs queues the observation offline on a retryable failure (never lost)", async () => {
    mockPost.mockRejectedValue(new Error("network down"));
    mockQueueOnRetryable.mockResolvedValue({ recordId: "local-obs-9", queued: true });
    const { recordObs } = await import("./epcrService");

    const res = await recordObs({ missionId: "m-1", eventType: "GCS", payload: { gcs: 9 } });

    expect(res.queued).toBe(true);
    expect(res.id).toBe("local-obs-9");
    expect(mockQueueOnRetryable).toHaveBeenCalledWith(
      expect.any(Error),
      expect.objectContaining({
        collection: "ems_epcr_event",
        path: "/internal/v1/daidzai/ems/missions/m-1/epcr/events",
      })
    );
  });

  it("recordObs rethrows when the error is not retryable (nothing queued)", async () => {
    mockPost.mockRejectedValue(new Error("400 bad request"));
    mockQueueOnRetryable.mockResolvedValue(null);
    const { recordObs } = await import("./epcrService");

    await expect(recordObs({ missionId: "m-1", eventType: "NOTE", payload: {} })).rejects.toThrow();
  });

  it("upsertEpcr posts the primary survey and can queue offline", async () => {
    mockPost.mockRejectedValue(new Error("timeout"));
    mockQueueOnRetryable.mockResolvedValue({ recordId: "local-epcr-m-1", queued: true });
    const { upsertEpcr } = await import("./epcrService");

    const res = await upsertEpcr("m-1", { primarySurvey: { airway: "patent" }, narrative: "RTC" });

    expect(res.queued).toBe(true);
    expect(mockQueueOnRetryable).toHaveBeenCalledWith(
      expect.any(Error),
      expect.objectContaining({ collection: "ems_epcr", operationType: "UPDATE" })
    );
  });

  it("getEpcr returns the ePCR view with its snapshot", async () => {
    mockGet.mockResolvedValue({
      data: { epcrId: "e-1", missionId: "m-1", events: [{ eventType: "VITALS" }], snapshot: { obsCount: 1 } },
    });
    const { getEpcr } = await import("./epcrService");

    const view = await getEpcr("m-1");

    expect(view.epcrId).toBe("e-1");
    expect(view.snapshot.obsCount).toBe(1);
    expect(mockGet).toHaveBeenCalledWith("/internal/v1/daidzai/ems/missions/m-1/epcr");
  });
});
