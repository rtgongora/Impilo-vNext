/**
 * Role-aware regulatory navigation (NCZ-W1B).
 *
 * The brief's separation of duties is the thing under test: a finance officer sees no examination
 * marks, an examination officer sees no refunds. These assert the resolution rules that make that
 * true — including the two cases where getting it wrong would be quiet rather than loud.
 */

import { describe, expect, it } from "vitest";
import {
  capabilityVerdict,
  requiresFacilityAssignment,
  roleWorkspaceFor,
  roleWorkspaces,
} from "../roleWorkspace";
import type { RegulatoryConfiguration } from "@/hooks/queries/useRegulatoryConfiguration";

function config(...definitions: Array<Record<string, unknown>>): RegulatoryConfiguration {
  return {
    organizationId: "org-1",
    packs: [
      {
        packId: "pack-1",
        packKey: "nurses-council",
        label: "Nurses Council of Zimbabwe",
        description: null,
        source: {
          loadState: "LOADED",
          loadFailureReason: null,
          loadCheckedAt: null,
          sourceRef: null,
          activatedConfigurationAffected: false,
        },
        releases: [],
        activated: true,
        definitionIndex: {},
        definitions: definitions.map((payload, index) => ({
          typeCode: "ROLE_WORKSPACE",
          definitionKey: `role-${index}`,
          label: `Role ${index}`,
          semanticVersion: "1.1.0",
          selfServiceRoute: null,
          contentHash: "a".repeat(64),
          pinnedAtMoment: null,
          policy: { status: null, decisionRef: null, valueSet: true },
          payload,
        })),
      },
    ],
  };
}

const FINANCE = {
  roleCode: "FINANCE_OFFICER",
  requiresFacilityAssignment: false,
  sees: ["INVOICES", "RECEIPTS", "REFUNDS", "RECONCILIATION"],
  neverSees: ["EXAMINATION_MARKS", "DISCIPLINARY_CASES"],
};

const EXAMS = {
  roleCode: "CPD_OFFICER",
  requiresFacilityAssignment: false,
  sees: ["EXAMINATION_SESSIONS", "EXAMINATION_MARKS"],
  neverSees: ["REFUNDS", "INVOICES"],
  capabilityStatus: "AWAITING_IMPLEMENTATION",
  capabilityNote: "Examinations are a deferred wave.",
  capabilityDecisionRefs: ["NCZ-DEC-004"],
};

describe("role workspaces", () => {
  it("reads only ROLE_WORKSPACE definitions from the activated configuration", () => {
    const withOther = config(FINANCE);
    withOther.packs[0].definitions.push({
      typeCode: "FEE_SCHEDULE",
      definitionKey: "student-index-fee",
      label: "Student index fee",
      semanticVersion: "1.0.0",
      selfServiceRoute: null,
      contentHash: "b".repeat(64),
      pinnedAtMoment: null,
      policy: { status: "PENDING_REGULATOR_APPROVAL", decisionRef: "NCZ-DEC-002", valueSet: false },
      payload: { amount: null },
    });

    expect(roleWorkspaces(withOther)).toHaveLength(1);
    expect(roleWorkspaceFor(withOther, "FINANCE_OFFICER")?.roleCode).toBe("FINANCE_OFFICER");
  });

  it("keeps money and marks apart in both directions", () => {
    const finance = roleWorkspaceFor(config(FINANCE, EXAMS), "FINANCE_OFFICER");
    const exams = roleWorkspaceFor(config(FINANCE, EXAMS), "CPD_OFFICER");

    expect(capabilityVerdict(finance, "EXAMINATION_MARKS")).toBe("WITHHOLD");
    expect(capabilityVerdict(finance, "REFUNDS")).toBe("OFFER");
    expect(capabilityVerdict(exams, "REFUNDS")).toBe("WITHHOLD");
  });

  it("withholds anything the role was not granted, not merely what was denied", () => {
    // A capability absent from BOTH lists must not be offered. Relying on neverSees alone would
    // mean every capability added later is silently granted to every existing role.
    const finance = roleWorkspaceFor(config(FINANCE), "FINANCE_OFFICER");
    expect(capabilityVerdict(finance, "SOMETHING_ADDED_LATER")).toBe("WITHHOLD");
  });

  it("resolves a conflict restrictively", () => {
    // Naming a capability in both lists is a configuration mistake. The safe reading of a mistake
    // about separation of duties is the restrictive one: "must never see" is the more deliberate
    // statement, and it must not be overridden by a broad grant.
    const conflicted = roleWorkspaceFor(
      config({ ...FINANCE, sees: [...FINANCE.sees, "EXAMINATION_MARKS"] }),
      "FINANCE_OFFICER",
    );
    expect(capabilityVerdict(conflicted, "EXAMINATION_MARKS")).toBe("WITHHOLD");
  });

  it("marks an unbuilt capability rather than withholding or linking it", () => {
    const exams = roleWorkspaceFor(config(EXAMS), "CPD_OFFICER");
    expect(capabilityVerdict(exams, "EXAMINATION_MARKS")).toBe("AWAITING_IMPLEMENTATION");
    expect(exams?.capabilityDecisionRefs).toContain("NCZ-DEC-004");
  });

  it("offers everything when no workspace resolves", () => {
    // This file is not the access boundary — the PDP is. Silently hiding a regulator's own work
    // because a configuration lookup failed would be a worse failure than showing a link that is
    // then refused.
    expect(capabilityVerdict(undefined, "EXAMINATION_MARKS")).toBe("OFFER");
    expect(roleWorkspaceFor(config(FINANCE), "A_ROLE_THE_PACK_DOES_NOT_DEFINE")).toBeUndefined();
    expect(roleWorkspaceFor(undefined, "FINANCE_OFFICER")).toBeUndefined();
  });

  it("never asks a council officer for a facility", () => {
    // Council staff are org-attached. A picker demanding a facility would block a registrar from
    // working at all, which is how this goes wrong in practice.
    expect(requiresFacilityAssignment(roleWorkspaceFor(config(FINANCE), "FINANCE_OFFICER"))).toBe(false);
    expect(requiresFacilityAssignment(undefined)).toBe(false);
  });
});
