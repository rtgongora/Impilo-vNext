import { describe, expect, it } from "vitest";
import {
  administersFacility,
  pendingAppointments,
  type FacilityAppointmentView,
} from "./useFacilityMode";

const rows: FacilityAppointmentView[] = [
  { id: 1, personHealthId: "hid-admin", approvalState: "APPROVED", role: "FACILITY_ADMIN" },
  { id: 2, personHealthId: "hid-pending", approvalState: "PENDING", role: "FACILITY_ADMIN" },
  { id: 3, personHealthId: "hid-other", approvalState: "APPROVED", role: "FACILITY_ADMIN" },
];

describe("useFacilityMode derivations (Facility Mode eligibility invariant)", () => {
  it("grants admin only to a person with an APPROVED appointment for the facility", () => {
    expect(administersFacility(rows, "hid-admin")).toBe(true);
  });

  it("denies admin to a person whose appointment is only PENDING", () => {
    expect(administersFacility(rows, "hid-pending")).toBe(false);
  });

  it("denies admin to a person with no appointment at all (no admin without an appointment)", () => {
    expect(administersFacility(rows, "hid-nobody")).toBe(false);
    expect(administersFacility(undefined, "hid-admin")).toBe(false);
    expect(administersFacility(rows, undefined)).toBe(false);
  });

  it("lists only pending appointments for the review queue", () => {
    const pend = pendingAppointments(rows);
    expect(pend).toHaveLength(1);
    expect(pend[0].personHealthId).toBe("hid-pending");
  });
});
