import { describe, expect, it } from "vitest";

describe("ubomiService contract", () => {
  it("exports citizen CRVS BFF paths", async () => {
    const mod = await import("./ubomiService");
    expect(typeof mod.fetchUbomiStatus).toBe("function");
    expect(typeof mod.verifyUbomiRegistration).toBe("function");
    expect(typeof mod.listUbomiBirthNotifications).toBe("function");
  });
});
