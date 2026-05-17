import { describe, expect, it } from "vitest";
import { integrationStatusCopy, integrationLine } from "../src/copy";

describe("Integration Service copy", () => {
  it("provides safe defaults for every status", () => {
    const statuses = [
      "CONNECTED",
      "PENDING",
      "PROCESSING",
      "AWAITING_EXTERNAL",
      "AVAILABLE",
      "FAILED",
      "RETRY_SCHEDULED",
      "ACTION_REQUIRED",
      "TEMPORARILY_UNAVAILABLE",
      "NOT_CONFIGURED",
      "DISABLED",
      "UNKNOWN",
    ] as const;

    for (const status of statuses) {
      const copy = integrationStatusCopy(status);
      expect(copy.label.length).toBeGreaterThan(0);
      expect(copy.message.length).toBeGreaterThan(0);
      expect(["info", "success", "warning", "danger", "muted"]).toContain(copy.severity);
    }
  });

  it("prefers a non-empty BFF-provided message over the default", () => {
    expect(integrationLine("FAILED", "Lab system rejected the order")).toBe(
      "Lab system rejected the order"
    );
  });

  it("falls back to default message when BFF message is empty", () => {
    expect(integrationLine("FAILED", "   ")).toMatch(/saved the reference/);
  });
});
