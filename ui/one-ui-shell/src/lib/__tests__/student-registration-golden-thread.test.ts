/**
 * Golden thread for the student registration journey (NCZ-W1C).
 *
 * The property worth guarding here is the one that is invisible when it holds: a training
 * institution confirming an enrolment must have no route to the rest of the student's application.
 * The boundary is the ROUTE — the contributor lane is addressed by invitation and cannot name an
 * application — so these assert the shape, because a shape cannot be forgotten the way a filter can.
 */

import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { ROUTES } from "../routes";

const root = join(__dirname, "..", "..");
const read = (p: string) => readFileSync(join(root, p), "utf8");

const HOOKS = "hooks/queries/useStudentRegistration.ts";
const STUDENT = "app/professional/regulatory/apply/student/[applicationId]/page.tsx";
const CONTRIBUTOR = "app/professional/regulatory/contribute/[inviteId]/page.tsx";
const REGULATOR = "app/work/regulatory/[orgId]/student-applications/[applicationId]/page.tsx";

describe("student registration — three sides", () => {
  it("registers all three routes", () => {
    const paths = ROUTES.map((r) => r.path);
    expect(paths).toContain("/professional/regulatory/apply/student/[applicationId]");
    expect(paths).toContain("/professional/regulatory/contribute/[inviteId]");
    expect(paths).toContain("/work/regulatory/[orgId]/student-applications/[applicationId]");
  });

  it("addresses the contributor by invitation and never by application", () => {
    // The route itself is the boundary. If this page could take an applicationId, one forgotten
    // scope check would put a training institution in a student's identity documents.
    const contributorRoute = ROUTES.find(
      (r) => r.path === "/professional/regulatory/contribute/[inviteId]",
    );
    expect(contributorRoute?.path).not.toContain("applicationId");
    expect(read(CONTRIBUTOR)).not.toContain("applicationId");
  });

  it("gives the contributor no hook that can reach an application", () => {
    const hooks = read(HOOKS);
    // Every contributor call must go through /contributions/{inviteId}.
    const contributorCalls = hooks.match(/\$\{BASE\}\/contributions[^`]*/g) ?? [];
    expect(contributorCalls.length).toBeGreaterThan(0);
    contributorCalls.forEach((call) => expect(call).not.toContain("applicationId"));
  });

  it("tells the contributor plainly what they cannot see", () => {
    // An institution that does not know the boundary exists will ask the student for the rest.
    const page = read(CONTRIBUTOR);
    expect(page).toContain("is not shared with you");
    expect(page).toContain("cannot");
  });

  it("tells a student that a returned section is not a rejection", () => {
    // This is the sentence the whole section model exists to make true.
    const page = read(STUDENT);
    expect(page).toContain("not starting again");
    expect(page).toContain("has not been rejected");
  });

  it("requires the regulator to say what needs to change before returning", () => {
    const page = read(REGULATOR);
    expect(page).toContain("say what needs to change");
    // The button must be disabled on an empty reason, not merely rejected by the server.
    expect(page).toMatch(/disabled=\{[^}]*reasons\[section\.sectionKey\]\?\.trim\(\)/);
  });

  it("shows the regulator an unset fee before the decision, never as free", () => {
    const page = read(REGULATOR);
    expect(page).toContain("is not set");
    expect(page).toContain("cannot be admitted until the Council sets");
    expect(page).toContain("nothing is waived");
    expect(page.toLowerCase()).not.toContain("no fee payable");
  });
});
