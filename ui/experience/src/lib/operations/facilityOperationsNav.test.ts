import { describe, expect, it } from "vitest";
import { filterFacilityOpsNavCards } from "./facilityOperationsNav";

describe("filterFacilityOpsNavCards", () => {
  it("always includes operational alerts (everyone) plus role-matched cards for queue manager", () => {
    const cards = filterFacilityOpsNavCards({
      isClinical: false,
      isAdmin: false,
      isFinance: false,
      isDispenser: false,
      isQueueManager: true,
    });
    const ids = cards.map((c) => c.id);
    expect(ids).toContain("alerts");
    expect(ids).toContain("control-tower");
    expect(ids).toContain("patient-flow");
    expect(ids).toContain("queues");
    expect(ids).toContain("roster");
    expect(ids).toContain("beds");
    expect(ids).toContain("tasks");
    expect(ids).not.toContain("handover");
    expect(ids).not.toContain("scheduling-roster");
    expect(ids).not.toContain("appointments");
  });

  it("shows clinical and queue surfaces for clinician mask", () => {
    const ids = filterFacilityOpsNavCards({
      isClinical: true,
      isAdmin: false,
      isFinance: false,
      isDispenser: false,
      isQueueManager: false,
    }).map((c) => c.id);
    expect(ids).toContain("appointments");
    expect(ids).toContain("handover");
    expect(ids).toContain("telemedicine");
    expect(ids).toContain("reports");
  });

  it("shows finance reporting entry for finance-only roles", () => {
    const ids = filterFacilityOpsNavCards({
      isClinical: false,
      isAdmin: false,
      isFinance: true,
      isDispenser: false,
      isQueueManager: false,
    }).map((c) => c.id);
    expect(ids).toContain("alerts");
    expect(ids).toContain("reports");
    expect(ids).not.toContain("queues");
  });
});
