/**
 * Provider facility-selection gating — doctrine test.
 *
 * The rule under test is a MEASURED FACT, not a policy preference: the HPA lane
 * counted zero workspaces attached to any of the 5,509
 * IMPORTED_PENDING_CONFIGURATION facilities. A work session binds to a facility
 * AND a workspace, so selecting one cannot produce a valid session under any
 * policy. The UI must therefore refuse to start a session there — while still
 * letting the tap through as an intent, because the person tapping may be the
 * practice owner who should configure it.
 *
 * Asserts against the real tuso status string, not a mocked shape.
 */

import { describe, it, expect } from "vitest";
import { isUnconfirmedFacility } from "@impilo/mobile-design-system";

/** Mirrors handleSelectFacility's branch in SelectFacilityScreen. */
function wouldStartWorkSession(regulatoryStatus?: string | null): boolean {
  return !isUnconfirmedFacility(regulatoryStatus);
}

describe("provider facility selection gating", () => {
  it("refuses to start a work session at an unconfigured facility", () => {
    // Zero workspaces exist for these; a session started here would be broken.
    expect(wouldStartWorkSession("IMPORTED_PENDING_CONFIGURATION")).toBe(false);
  });

  it("starts normally at a configured facility", () => {
    expect(wouldStartWorkSession("REGISTERED_ACTIVE")).toBe(true);
    expect(wouldStartWorkSession("PENDING_INSPECTION")).toBe(true);
  });

  it("starts normally when status is absent (pre-deploy shape)", () => {
    // Until the tuso roll ships regulatoryStatus, the field is null. Provider
    // selection must keep working exactly as before — no new blocking.
    expect(wouldStartWorkSession(null)).toBe(true);
    expect(wouldStartWorkSession(undefined)).toBe(true);
  });
});
