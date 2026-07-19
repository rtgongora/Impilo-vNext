import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

/**
 * HPA-W4b golden thread: the admin HPA review console → useHpaImport → BFF admin
 * HPA lanes → tuso HpaImportController (runs / outcomes / review-queue).
 */
describe("HPA admin review golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");
  const read = (p: string) => readFileSync(resolve(repoRoot, p), "utf8");

  it("the admin page renders batches, outcomes and the review queue", () => {
    const page = read("ui/one-ui-shell/src/app/admin/hpa-enrichment/page.tsx");
    expect(page).toContain("useHpaImportRuns");
    expect(page).toContain("useHpaOutcomes");
    expect(page).toContain("useHpaReviewQueue");
    expect(page).toContain("hpa-run-row");
    expect(page).toContain("hpa-review-row");
  });

  it("the route is registered (REGISTRY_ADMIN-gated)", () => {
    const routes = read("ui/one-ui-shell/src/lib/routes.ts");
    expect(routes).toMatch(/\/admin\/hpa-enrichment[\s\S]*?REGISTRY_ADMIN/);
    expect(routes).toContain("EXPECTED_ROUTE_COUNT = 758");
  });

  it("the hook targets the BFF admin HPA lanes", () => {
    const hook = read("ui/one-ui-shell/src/hooks/queries/useHpaImport.ts");
    expect(hook).toContain('"/internal/v1/admin"');
    expect(hook).toContain("/hpa-import-runs");
    expect(hook).toContain("/outcomes");
    expect(hook).toContain("/review-queue");
  });

  it("the BFF proxies to the tuso HpaImportController", () => {
    const bff = read(
      "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/AdminFacilityImportController.java",
    );
    expect(bff).toContain("/hpa-import-runs");
    expect(bff).toContain("hpaImportRuns()");
    expect(bff).toContain("hpaImportReviewQueue");
    const client = read(
      "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/client/TusoServiceClient.java",
    );
    expect(client).toContain("/v1/internal/facilities/hpa-import/runs");
  });
});
