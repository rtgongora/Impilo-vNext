import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

/**
 * Steward console golden thread: the Place Governance Console reviews the place
 * self-service case types the IATG Trust Console does NOT own — facility & site
 * registration, site operator grants, and facility verification & governance —
 * decides them via the already-wired BFF lanes plus the two new decision lanes.
 */
describe("place governance console golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");
  const read = (p: string) => readFileSync(resolve(repoRoot, p), "utf8");

  it("the console wires every steward queue + decision hook", () => {
    const page = read("ui/one-ui-shell/src/app/registry/place-governance/page.tsx");
    expect(page).toContain("useFacilityRegistrationCases");
    expect(page).toContain("useSiteRegistrationCases");
    expect(page).toContain("useOperatorGrantQueue");
    expect(page).toContain("useApproveOperatorGrant");
    expect(page).toContain("useDecideFacilityVerification");
    expect(page).toContain("useDecideFacilityGovernance");
  });

  it("the console route is steward-gated (REGISTRY_ADMIN)", () => {
    const routes = read("ui/one-ui-shell/src/lib/routes.ts");
    expect(routes).toContain("/registry/place-governance");
    expect(routes).toMatch(/place-governance[\s\S]*?REGISTRY_ADMIN/);
  });

  it("the decision hooks target the BFF decision lanes", () => {
    const hook = read("ui/one-ui-shell/src/hooks/queries/useFacilityLifecycle.ts");
    expect(hook).toContain("/verification-cases/");
    expect(hook).toContain("/governance/cases/");
    expect(hook).toContain("/decision");
  });

  it("the BFF exposes the two new decision endpoints", () => {
    const controller = read(
      "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/FacilityLifecycleController.java",
    );
    expect(controller).toContain("/verification-cases/{caseId}/decision");
    expect(controller).toContain("/governance/cases/{caseId}/decision");
    expect(controller).toContain("decideVerificationCase");
    expect(controller).toContain("decideGovernanceCase");
  });

  it("the BFF client hits the tuso decision endpoints", () => {
    const client = read(
      "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/client/TusoFacilityLifecycleClient.java",
    );
    expect(client).toContain("decideVerificationCase");
    expect(client).toContain("decideGovernanceCase");
  });
});
