/**
 * Facility disclosure — safety tests.
 *
 * These assert on the REAL tuso value (`IMPORTED_PENDING_CONFIGURATION`) and the
 * real component, not on a mocked shape. Today's lesson from the mobile auth
 * defect: ModeRouter.test.tsx hardcoded `realm_access: { roles: ["provider"] }`
 * and so asserted a fiction production never produced. A test that invents its
 * own input proves nothing about the system.
 *
 * What must hold, in patient-safety terms: a facility the HPA has merely listed
 * must never be presented to a citizen as open, and its address/phone must never
 * read as confirmed fact.
 */

import { describe, it, expect } from "vitest";
import { isUnconfirmedFacility, UNCONFIRMED_REGULATORY_STATUS } from "./regulatory-status";

describe("isUnconfirmedFacility", () => {
  it("treats the real tuso HPA-import status as unconfirmed", () => {
    // The literal value tuso writes for the 5,509 HPA-imported facilities.
    expect(isUnconfirmedFacility("IMPORTED_PENDING_CONFIGURATION")).toBe(true);
    expect(UNCONFIRMED_REGULATORY_STATUS).toBe("IMPORTED_PENDING_CONFIGURATION");
  });

  it("does NOT mark operating facilities as unconfirmed (no false alarms)", () => {
    // A disclosure on every facility would be noise, and noise gets ignored —
    // which would defeat the warning where it actually matters.
    expect(isUnconfirmedFacility("REGISTERED_ACTIVE")).toBe(false);
    expect(isUnconfirmedFacility("PENDING_INSPECTION")).toBe(false);
  });

  it("fails SAFE-BY-DISCLOSURE only for the known status, not for unknown ones", () => {
    // Deliberate: an unrecognised status is not silently treated as unconfirmed,
    // because that would make the banner meaningless as tuso adds states. New
    // states must be added explicitly and consciously.
    expect(isUnconfirmedFacility("SOME_FUTURE_STATE")).toBe(false);
  });

  it("handles absent status — the pre-deploy state of /internal/v1/facilities", () => {
    // Until the tuso roll ships regulatoryStatus on the internal controller the
    // field arrives null. That must not crash and must not fabricate a warning.
    expect(isUnconfirmedFacility(null)).toBe(false);
    expect(isUnconfirmedFacility(undefined)).toBe(false);
  });

  it("is case-insensitive so a serialisation change cannot silently drop the warning", () => {
    expect(isUnconfirmedFacility("imported_pending_configuration")).toBe(true);
  });
});
