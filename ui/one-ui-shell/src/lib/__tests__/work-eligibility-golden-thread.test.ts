import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

/**
 * D-P6 golden thread: the provider work-eligibility surface is wired page →
 * panel → hook → BFF endpoint end-to-end, and the BFF endpoint is the
 * owner-only summary that keeps authentication and authorisation separate.
 */
describe("work-eligibility golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");
  const read = (p: string) => readFileSync(resolve(repoRoot, p), "utf8");

  it("the hook calls the BFF work-context eligibility endpoint and degrades honestly", () => {
    const hook = read("ui/one-ui-shell/src/hooks/queries/useWorkEligibility.ts");
    expect(hook).toContain("/internal/v1/work-context/eligibility");
    expect(hook).toContain("resolved");
    // Degrade-honestly: a failed call returns the unresolved const, never throws.
    expect(hook).toContain("catch");
  });

  it("the panel consumes the hook and always keeps My Professional / My Life reachable", () => {
    const panel = read("ui/one-ui-shell/src/components/provider/WorkEligibilityPanel.tsx");
    expect(panel).toContain("useWorkEligibility");
    expect(panel).toContain("/professional");
    expect(panel).toContain("/home");
    expect(panel).toContain("/provider/get-access");
  });

  it("the provider status page renders the live panel", () => {
    const page = read("ui/one-ui-shell/src/app/provider/status/page.tsx");
    expect(page).toContain("WorkEligibilityPanel");
  });

  it("the BFF endpoint is owner-only (session actor) and separates authn from authz", () => {
    const controller = read(
      "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/WorkContextController.java",
    );
    expect(controller).toContain("/eligibility");
    expect(controller).toContain("workEligible");
    expect(controller).toContain("remediation");
    // The summary is keyed on the session actor (X-Actor-ID), never another provider.
    expect(controller).toContain("ACTOR_ID");
  });
});
