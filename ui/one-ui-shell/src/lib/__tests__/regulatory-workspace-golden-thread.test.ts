import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { ROUTES } from "@/lib/routes";
import { OPERATIONAL_MODE_DEF, OPERATIONAL_MODE_ORDER } from "@/lib/operational-context";
import { REGULATORY_ORGS, regulatoryOrg, roleLabel } from "@/lib/regulatory/organisations";

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

  it("knows the nine organisations with their deterministic org-registry UUIDs", () => {
    expect(REGULATORY_ORGS).toHaveLength(9);
    expect(regulatoryOrg("a5000000-0000-4000-8000-000000000003")?.code).toBe("NCZ");
    expect(regulatoryOrg("a5000000-0000-4000-8000-000000000001")?.kind).toBe("authority");
    expect(roleLabel("REGISTRATION_OFFICER")).toBe("Registration Officer");
  });
});
