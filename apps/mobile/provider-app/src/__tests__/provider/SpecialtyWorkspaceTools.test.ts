/**
 * Guards the burns withdrawal in SpecialtyWorkspacePanel.
 *
 * Acute burns %TBSA and Parkland fluid resuscitation are owned by the Emergency,
 * Resuscitation and Acute Care pack (`docs/registry/iatg-emergency-leases.md` §5b) and
 * land in `libs/burn-domain`, age-banded via `libs/paediatric-domain` on `bandKey`. (Not
 * `libs/emergency-domain`: %TBSA drives excision timing, graft planning, nutrition and mortality
 * prediction for months after the emergency episode closes, so the arithmetic is shared with
 * surgery rather than owned by emergency. Agreed with the surgery lane, lease §5b Decision 1.)
 * This app must not carry a private implementation: the one it used to carry was
 * adult-only, had no injury-time clock or first-8h/second-16h split, and persisted
 * nothing while telling the clinician it had saved.
 *
 * If a burns calculator reappears here, this test fails.
 */
import { describe, expect, it } from "vitest";
import { formKindForTool } from "../../screens/provider/SpecialtyWorkspacePanel";
import { getSpecialtyById } from "../../data/specialtyWorkspaces";

const BURNS = getSpecialtyById("burns");

describe("burns workspace tools", () => {
  it("still exists as a workspace", () => {
    expect(BURNS).toBeDefined();
  });

  it("offers no tool that computes a number", () => {
    const numericKinds = ["bsa", "ktv", "sum"];
    const computing = (BURNS?.tools ?? [])
      .map((tool, index) => ({ tool, kind: formKindForTool(tool, index, "burns") }))
      .filter((t) => numericKinds.includes(t.kind));
    expect(computing).toEqual([]);
  });

  it("withholds the Rule of 9s assessment rather than showing an adult-only chart", () => {
    expect(formKindForTool("Burns Assessment (Rule of 9s)", 0, "burns")).toBe("withheld");
  });

  it("withholds Parkland rather than showing an unclocked 24h total", () => {
    expect(formKindForTool("Fluid Resuscitation (Parkland)", 1, "burns")).toBe("withheld");
  });

  it("withholds the burns tool names the BFF menu advertises", () => {
    // services/experience-bff/.../MobileProviderExtendedController.java:546 advertises a
    // different burns tool set than specialtyWorkspaces.ts. Whichever menu wins, the
    // arithmetic stays withheld.
    for (const tool of ["Lund-Browder Chart", "Fluid Calculator", "Parkland Formula"]) {
      expect(formKindForTool(tool, 0, "burns")).toBe("withheld");
    }
  });

  it("does not label a generic two-number adder as a burns clinical tool", () => {
    expect(formKindForTool("Graft Planning", 3, "burns")).toBe("soon");
  });
});

describe("unrelated specialty tools are unaffected", () => {
  it("keeps the chemotherapy BSA dose calculator", () => {
    expect(formKindForTool("Dose Calculator (BSA)", 1, "chemo")).toBe("bsa");
  });

  it("keeps the dialysis Kt/V calculator", () => {
    expect(formKindForTool("Kt/V Calculator", 2, "dialysis")).toBe("ktv");
  });

  it("keeps the generic calculator fallback outside withheld workspaces", () => {
    expect(formKindForTool("Heart Failure Assessment", 3, "cardiology")).toBe("sum");
  });

  it("keeps the pre-chemo checklist", () => {
    expect(formKindForTool("Pre-Chemo Checklist", 2, "chemo")).toBe("checklist");
  });

  it("keeps tools past the fourth as coming-soon overviews", () => {
    expect(formKindForTool("Nutrition Plan", 5, "burns")).toBe("soon");
    expect(formKindForTool("Cardiac Rehab", 5, "cardiology")).toBe("soon");
  });
});
