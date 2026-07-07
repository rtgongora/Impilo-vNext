import { describe, expect, it } from "vitest";
import {
  administersFacility,
  pendingAppointments,
  type FacilityAppointmentView,
} from "./useFacilityMode";

// Backend (tuso FacilityAdminAppointmentEntity) emits approvalState "ACTIVE" for
// an approved admin — never "APPROVED". These fixtures use the REAL backend
// literal so the gate is tested against the true contract.
const rows: FacilityAppointmentView[] = [
  { id: 1, personHealthId: "hid-admin", approvalState: "ACTIVE", role: "FACILITY_ADMINISTRATOR" },
  { id: 2, personHealthId: "hid-pending", approvalState: "PENDING", role: "FACILITY_ADMINISTRATOR" },
  { id: 3, personHealthId: "hid-other", approvalState: "ACTIVE", role: "FACILITY_ADMINISTRATOR" },
];

describe("useFacilityMode derivations (Facility Mode eligibility invariant)", () => {
  it("grants admin only to a person with an ACTIVE appointment for the facility", () => {
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

  it("does NOT grant on the string 'APPROVED', which tuso never emits (regression guard)", () => {
    const legacy: FacilityAppointmentView[] = [
      { id: 9, personHealthId: "hid-x", approvalState: "APPROVED", role: "FACILITY_ADMINISTRATOR" },
    ];
    expect(administersFacility(legacy, "hid-x")).toBe(false);
  });

  it("lists only pending appointments for the review queue", () => {
    const pend = pendingAppointments(rows);
    expect(pend).toHaveLength(1);
    expect(pend[0].personHealthId).toBe("hid-pending");
  });
});
