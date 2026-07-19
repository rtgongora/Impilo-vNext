import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

/**
 * PJ4/D-P7 golden thread: the facility-access request page → hook → BFF
 * access-request lane → varapi FACILITY_ACCESS request, and the applicant view
 * surfaces the facility/engagement/validity so status is meaningful.
 */
describe("facility-access request golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");
  const read = (p: string) => readFileSync(resolve(repoRoot, p), "utf8");

  it("the page submits a FACILITY_ACCESS request with engagement + validity", () => {
    const page = read("ui/one-ui-shell/src/app/provider/facility-access/page.tsx");
    expect(page).toContain("useSubmitProviderAccessRequest");
    expect(page).toContain("FACILITY_ACCESS");
    expect(page).toContain("engagementType");
    // A temporary engagement must be end-dated (client-side mirror of the server rule).
    expect(page).toContain("temporary engagement needs an access end date");
  });

  it("the hook knows the FACILITY_ACCESS type + fields", () => {
    const hook = read("ui/one-ui-shell/src/hooks/queries/useProviderAccessRequest.ts");
    expect(hook).toContain("FACILITY_ACCESS");
    expect(hook).toContain("ENGAGEMENT_TYPES");
    expect(hook).toContain("accessValidTo");
    expect(hook).toContain("/api/v1/provider-claim/access-request");
  });

  it("the BFF forwards the whole body to varapi (transparent passthrough)", () => {
    const controller = read(
      "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/ProviderClaimController.java",
    );
    expect(controller).toContain("/access-request");
    expect(controller).toContain("submitProviderAccessRequest(body)");
  });

  it("the varapi applicant view surfaces facility/engagement/validity (W5)", () => {
    const view = read(
      "services/varapi-service/src/main/java/zw/gov/mohcc/impilo/varapi/api/dto/ProviderAccessRequestView.java",
    );
    expect(view).toContain("facilityId");
    expect(view).toContain("engagementType");
    expect(view).toContain("accessValidTo");
  });
});
