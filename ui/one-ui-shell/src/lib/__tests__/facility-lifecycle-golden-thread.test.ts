import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

/**
 * UI-5 golden thread (D-L4/D-L6/D-L8): facility registration / verification /
 * trust / governance pages → useFacilityLifecycle → BFF FacilityLifecycleController
 * → TusoFacilityLifecycleClient → tuso facility SoR. The BFF binds the applicant
 * to X-Actor-ID and gates submits on person assurance (≥LOA2).
 */
describe("facility lifecycle golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");
  const read = (p: string) => readFileSync(resolve(repoRoot, p), "utf8");

  it("the register page submits via the hook", () => {
    const page = read("ui/one-ui-shell/src/app/facility/register/page.tsx");
    expect(page).toContain("useRegisterFacility");
    expect(page).toContain("evidence"); // authority half is mandatory
  });

  it("the trust page reads dimensions + opens verification + raises governance", () => {
    const page = read("ui/one-ui-shell/src/app/facility/[id]/trust/page.tsx");
    expect(page).toContain("useFacilityTrustDimensions");
    expect(page).toContain("useOpenFacilityVerification");
    expect(page).toContain("useOpenFacilityGovernanceCase");
  });

  it("the hook targets the facility-lifecycle BFF lane", () => {
    const hook = read("ui/one-ui-shell/src/hooks/queries/useFacilityLifecycle.ts");
    expect(hook).toContain("/api/v1/facility-lifecycle");
    expect(hook).toContain("/registrations");
    expect(hook).toContain("/verification-cases");
    expect(hook).toContain("/trust-dimensions");
    expect(hook).toContain("/governance/");
  });

  it("the BFF binds applicant to X-Actor-ID and gates submit on assurance", () => {
    const controller = read(
      "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/FacilityLifecycleController.java",
    );
    expect(controller).toContain("/api/v1/facility-lifecycle");
    // Applicant is the session identity, never the body.
    expect(controller).toContain('req.put("personHealthId", actorId)');
    expect(controller).toContain("MIN_SUBMIT_ASSURANCE_RANK");
    expect(controller).toContain("IDENTITY_PROOFING_REQUIRED");
  });

  it("the BFF client hits the tuso facility SoR endpoints", () => {
    const client = read(
      "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/client/TusoFacilityLifecycleClient.java",
    );
    expect(client).toContain("/v1/internal/facility-registrations");
    expect(client).toContain("/verification-cases");
    expect(client).toContain("/trust-dimensions");
    expect(client).toContain("/governance/");
  });
});
