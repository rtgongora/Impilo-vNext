import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

import { ROUTES } from "@/lib/routes";

/**
 * HAR W6/W7 golden thread — the work has to be reachable by a person, and the screen must not
 * promise more than the backend allows.
 *
 * The HPA import produced 24,616 data-gap tasks across 5,509 facilities and 6,180 PIC nominations.
 * Both were reachable only one facility at a time, which meant the work existed and nobody could do
 * it. These assertions pin the screen that fixes that, and the two honesty constraints on it.
 */
describe("registry worklist", () => {
  const page = readFileSync(
    join(process.cwd(), "src/app/registry/facilities/worklist/page.tsx"),
    "utf8",
  );
  const hooks = readFileSync(
    join(process.cwd(), "src/hooks/queries/useFacilityRegulatory.ts"),
    "utf8",
  );

  it("is a registered route", () => {
    expect(ROUTES.some((r) => r.path === "/registry/facilities/worklist")).toBe(true);
  });

  it("covers both queues that were unreachable", () => {
    expect(page).toContain("Data gaps");
    expect(page).toContain("Practitioner-in-charge nominations");
    expect(hooks).toContain("useDataGapWorklist");
    expect(hooks).toContain("usePicNominationQueue");
  });

  it("filters along the axes a steward actually works", () => {
    expect(page).toContain("All dimensions");
    expect(page).toContain("All priorities");
    expect(page).toContain("All provinces");
  });

  /**
   * The backend refuses any status past RESOLVED, because a data gap closes when evidence arrives.
   * The screen must not offer an action the backend would reject, and must not imply that clearing
   * a row verifies anything.
   */
  it("offers no action that would mark a facility verified", () => {
    expect(page).toContain('transition("RESOLVED")');
    expect(page).toContain('transition("IN_PROGRESS")');
    expect(page).not.toContain('transition("VERIFIED")');
    expect(page).toContain("it does not mark the facility verified");
  });

  /**
   * A nomination parked at ELIGIBILITY_CHECKED is the state machine working — the practitioner has
   * not claimed their profile. If the screen framed it as a fault, a registrar would try to force
   * it, which is exactly the fabricated-authority path the backend refuses.
   */
  it("explains that a parked nomination is correct, not broken", () => {
    expect(page).toContain("ELIGIBILITY_CHECKED");
    expect(page).toContain("state machine working");
    expect(page).toContain("nothing here creates an assignment");
  });

  it("pages rather than truncating", () => {
    expect(page).toContain("totalElements");
    expect(page).toContain("hasNext");
  });
});

/**
 * HAR W6 — 3,654 ingested contacts were rendered as nothing, so a facility with no pin and no
 * confirmed hours also offered no way to ask whether it was open.
 */
describe("public facility contacts", () => {
  const detail = readFileSync(
    join(process.cwd(), "src/components/public/find-care/FindCareFacilityDetail.tsx"),
    "utf8",
  );
  const types = readFileSync(join(process.cwd(), "src/lib/find-care/types.ts"), "utf8");

  it("renders the facility's contact points", () => {
    expect(detail).toContain("How to reach this facility");
    expect(detail).toContain("profile.contacts");
  });

  it("marks unconfirmed numbers rather than presenting them as fact", () => {
    expect(detail).toContain("Not confirmed");
    expect(detail).toContain("call before travelling");
    expect(detail).toContain("contact.verified === false");
  });

  /**
   * The projection tuso sends carries no name and no role — a contact person is a person. If the
   * type ever grew those fields the UI could render them, so the type is the guard.
   */
  it("the contact type exposes no person", () => {
    const block = types.slice(types.indexOf("export interface FacilityContact"));
    const body = block.slice(0, block.indexOf("}"));
    expect(body).toContain("phone");
    expect(body).toContain("email");
    expect(body).not.toContain("name");
    expect(body).not.toContain("role");
  });
});
