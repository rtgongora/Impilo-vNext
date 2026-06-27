import { describe, expect, it } from "vitest";
import { isStepUpRequired, readStepUp, stepUpMethodLabel } from "./stepUp";

describe("stepUp detection (G-CZO-04)", () => {
  it("recognises a STEP_UP_REQUIRED body via verdict/decision/errorCode", () => {
    expect(readStepUp({ verdict: "STEP_UP_REQUIRED", stepUpMethods: ["MFA"] })).toEqual({
      required: true,
      methods: ["MFA"],
    });
    expect(isStepUpRequired({ decision: "STEP_UP_REQUIRED" })).toBe(true);
    expect(isStepUpRequired({ errorCode: "STEP_UP_REQUIRED", stepUpMethods: ["SMS_OTP", "BIOMETRIC"] })).toBe(true);
  });

  it("does NOT treat a session-expiry or generic 401 as step-up", () => {
    expect(isStepUpRequired({ status: 401, error: { code: "SESSION_EXPIRED" } })).toBe(false);
    expect(isStepUpRequired({ status: 403, errorCode: "POLICY_DENY" })).toBe(false);
    expect(isStepUpRequired(null)).toBe(false);
    expect(isStepUpRequired(undefined)).toBe(false);
  });

  it("maps engine method tokens to citizen-friendly labels", () => {
    expect(stepUpMethodLabel("MFA")).toBe("Authenticator app code");
    expect(stepUpMethodLabel("SMS_OTP")).toBe("SMS one-time code");
    expect(stepUpMethodLabel("BIOMETRIC")).toBe("Device biometric");
    expect(stepUpMethodLabel("SUPERVISOR_APPROVAL")).toBe("Supervisor approval");
    expect(stepUpMethodLabel("UNKNOWN")).toBe("UNKNOWN");
  });
});
