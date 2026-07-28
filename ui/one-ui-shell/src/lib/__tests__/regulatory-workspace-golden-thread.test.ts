import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { ROUTES } from "@/lib/routes";
import { OPERATIONAL_MODE_DEF, OPERATIONAL_MODE_ORDER } from "@/lib/operational-context";
import * as regulatoryVocabulary from "@/lib/regulatory/organisations";
import { roleLabel } from "@/lib/regulatory/organisations";

/**
 * Golden thread for the ROM-W2 org-scoped regulatory workspace: the login-context seam is
 * surfaced (mode + routes + picker) and bound to the real BFF org-session lane.
 */
const SRC = join(process.cwd(), "src");
const read = (p: string) => readFileSync(join(SRC, p), "utf8");

describe("ROM-W2 regulatory workspace", () => {
  it("registers the regulatory routes", () => {
    const paths = ROUTES.map((r) => r.path);
    expect(paths).toContain("/work/regulatory");
    expect(paths).toContain("/work/regulatory/[orgId]");
  });

  it("adds the regulatory_work operational mode", () => {
    expect(OPERATIONAL_MODE_ORDER).toContain("regulatory_work");
    expect(OPERATIONAL_MODE_DEF.regulatory_work.expectsFacilityWorkSequence).toBe(false);
  });

  it("picker starts an ORG-scoped session (no facility) via the BFF work-context lane", () => {
    const page = read("app/work/regulatory/page.tsx");
    expect(page).toContain("/internal/v1/work-context/regulatory/appointments");
    expect(page).toContain("/internal/v1/work-context/session");
    expect(page).toContain("organisationId");
    expect(page).not.toContain("facilityId");
  });

  it("no longer keeps its own copy of the nine organisations", () => {
    // This assertion is deliberately inverted from the one it replaces. The hardcoded array was the
    // third copy in the estate — after varapi V028 and org-registry V007 — and the only one that
    // could drift with nothing failing: a council renamed or retired in the registry would have
    // left the interface confidently showing the old name.
    expect(regulatoryVocabulary).not.toHaveProperty("REGULATORY_ORGS");
    expect(regulatoryVocabulary).not.toHaveProperty("regulatoryOrg");
  });

  it("resolves organisations from org-registry through the BFF instead", () => {
    const hook = read("hooks/queries/useRegulatoryOrganisations.ts");
    expect(hook).toContain("/api/v1/regulatory/organizations");
    // No deterministic UUID may reappear here — restating one is how the copy came back last time.
    expect(hook).not.toMatch(/a5000000-0000-4000-8000-/);
  });

  it("keeps the appointment-role labels, which are wording rather than a second registry", () => {
    expect(roleLabel("REGISTRATION_OFFICER")).toBe("Registration Officer");
  });
});
