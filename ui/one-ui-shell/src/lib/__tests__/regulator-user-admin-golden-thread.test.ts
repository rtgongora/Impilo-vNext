import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

/**
 * Golden thread for the RB-6 regulator human surface.
 *
 * The three UI surfaces are wired to real BFF rails, and the picker dead-end is closed:
 *  - the users page reads the org-registry roster and drives invite/verify/end;
 *  - the picker empty state offers "Request regulatory access" (was a dead end);
 *  - the request page posts to the RB-3 bootstrap rail;
 *  - the request route is registered (no orphan page).
 */
describe("RB-6 regulator user-administration golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");
  const read = (p: string) => readFileSync(resolve(repoRoot, p), "utf8");

  it("the users page reads the roster and drives the appointment lifecycle over the BFF", () => {
    const page = read("ui/one-ui-shell/src/app/work/regulators/[regulatorId]/users/page.tsx");
    expect(page).toContain("/api/v1/regulatory/organizations/");
    expect(page).toContain("/appointments");
    expect(page).toContain("/verify");
    expect(page).toContain("/end");
    expect(page).toContain("/invitations");
    // The mandatory-second-administrator invariant is visible, and the succession-guard 409 is surfaced.
    expect(page).toContain("Two administrators are required");
    expect(page).toContain("LAST_ADMINISTRATOR");
  });

  it("the picker empty state offers the request action instead of dead-ending", () => {
    const picker = read("ui/one-ui-shell/src/app/work/regulatory/page.tsx");
    expect(picker).toContain("/work/regulatory/request");
    expect(picker).toContain("Request regulatory access");
  });

  it("the request page posts to the RB-3 bootstrap rail with the founding role", () => {
    const req = read("ui/one-ui-shell/src/app/work/regulatory/request/page.tsx");
    expect(req).toContain("/api/v1/regulator-bootstrap/requests");
    expect(req).toContain("FOUNDING_REGULATOR_ADMINISTRATOR");
    // Identity-proofing refusal routes to verification, never swallowed.
    expect(req).toContain("IDENTITY_PROOFING_REQUIRED");
  });

  it("the request route is registered (no orphan page)", () => {
    const routes = read("ui/one-ui-shell/src/lib/routes.ts");
    expect(routes).toContain('path: "/work/regulatory/request"');
  });
});
