import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

/**
 * UI-6 golden thread (SJ1/SJ2 / D-L4): site registration + operator-access pages
 * → useSiteSelfService → BFF SiteSelfServiceController → IndawoServiceClient →
 * indawo site SoR. The BFF binds the applicant to X-Actor-ID, gates submits on
 * person assurance, and is DISTINCT from the regulatory site-registry rail.
 */
describe("site self-service golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");
  const read = (p: string) => readFileSync(resolve(repoRoot, p), "utf8");

  it("the register + operator-access pages submit via the hooks", () => {
    const register = read("ui/one-ui-shell/src/app/site/register/page.tsx");
    expect(register).toContain("useRegisterSite");
    const operator = read("ui/one-ui-shell/src/app/site/operator-access/page.tsx");
    expect(operator).toContain("useRequestOperatorGrant");
    expect(operator).toContain("SITE_OPERATOR_ROLES");
  });

  it("the hook targets the site self-service BFF lane", () => {
    const hook = read("ui/one-ui-shell/src/hooks/queries/useSiteSelfService.ts");
    expect(hook).toContain("/api/v1/site-self-service");
    expect(hook).toContain("/registrations");
    expect(hook).toContain("/operator-grants");
  });

  it("the BFF binds applicant to X-Actor-ID and gates submit on assurance", () => {
    const controller = read(
      "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/SiteSelfServiceController.java",
    );
    expect(controller).toContain("/api/v1/site-self-service");
    expect(controller).toContain('req.put("personHealthId", actorId)');
    expect(controller).toContain("MIN_SUBMIT_ASSURANCE_RANK");
    expect(controller).toContain("IDENTITY_PROOFING_REQUIRED");
  });

  it("the BFF client hits the indawo self-service endpoints (not the regulatory rail)", () => {
    const client = read(
      "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/client/IndawoServiceClient.java",
    );
    expect(client).toContain("/internal/v1/site-registrations");
    expect(client).toContain("/operator-grants");
  });
});
