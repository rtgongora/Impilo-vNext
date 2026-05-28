import { describe, expect, it } from "vitest";
import { resolveTelemedicineWorkflowIndex, TELEMEDICINE_WORKFLOW_STAGES } from "./telemedicine-workflow-stages";

describe("resolveTelemedicineWorkflowIndex", () => {
  it("prefers explicit bffStage in 1..7", () => {
    expect(resolveTelemedicineWorkflowIndex({ status: "REQUESTED", bffStage: 4 })).toBe(3);
    expect(resolveTelemedicineWorkflowIndex({ status: null, bffStage: 1 })).toBe(0);
    expect(resolveTelemedicineWorkflowIndex({ status: "IN_PROGRESS", bffStage: 7 })).toBe(6);
  });

  it("ignores invalid bffStage and falls back to status", () => {
    expect(resolveTelemedicineWorkflowIndex({ status: "SCHEDULED", bffStage: 0 })).toBe(2);
    expect(resolveTelemedicineWorkflowIndex({ status: "SCHEDULED", bffStage: 99 })).toBe(2);
    expect(resolveTelemedicineWorkflowIndex({ status: "SCHEDULED", bffStage: Number.NaN })).toBe(2);
  });

  it("maps known statuses when bffStage absent", () => {
    expect(resolveTelemedicineWorkflowIndex({ status: "NEW" })).toBe(0);
    expect(resolveTelemedicineWorkflowIndex({ status: "TRIAGED" })).toBe(1);
    expect(resolveTelemedicineWorkflowIndex({ status: "ROUTED" })).toBe(1);
    expect(resolveTelemedicineWorkflowIndex({ status: "ACCEPTED" })).toBe(2);
    expect(resolveTelemedicineWorkflowIndex({ status: "PREPARED" })).toBe(3);
    expect(resolveTelemedicineWorkflowIndex({ status: "IN_CALL" })).toBe(4);
    expect(resolveTelemedicineWorkflowIndex({ status: "COMPLETED" })).toBe(5);
    expect(resolveTelemedicineWorkflowIndex({ status: "CLOSED" })).toBe(6);
  });

  it("exposes seven stages", () => {
    expect(TELEMEDICINE_WORKFLOW_STAGES).toHaveLength(7);
  });
});
