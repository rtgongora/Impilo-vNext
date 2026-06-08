import { describe, expect, it } from "vitest";
import { nextFieldTaskStatus } from "./publicHealthService";

describe("publicHealthService", () => {
  it("advances field task lifecycle", () => {
    expect(nextFieldTaskStatus("PLANNED")).toBe("ASSIGNED");
    expect(nextFieldTaskStatus("IN_PROGRESS")).toBe("COMPLETED");
  });

  it("exports createFieldTask for outreach parity", async () => {
    const mod = await import("./publicHealthService");
    expect(typeof mod.createFieldTask).toBe("function");
  });
});
