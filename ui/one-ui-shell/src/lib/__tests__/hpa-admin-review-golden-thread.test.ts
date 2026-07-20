import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { EXPECTED_ROUTE_COUNT, ROUTES } from "@/lib/routes";

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

  it("the console can TRIGGER an import and shows cross-service queues", () => {
    const page = read("ui/one-ui-shell/src/app/admin/hpa-enrichment/page.tsx");
    // Operable: run dry-run / apply, not just read.
    expect(page).toContain("useHpaDryRun");
    expect(page).toContain("useHpaApply");
    expect(page).toContain("hpa-run-import");
    // Cross-service candidate visibility (Varapi practitioners + Ndila geocode).
    expect(page).toContain("useHpaPractitionerOnboarding");
    expect(page).toContain("useHpaGeocodeReview");
    expect(page).toContain("hpa-onboarding-row");
    expect(page).toContain("hpa-geocode-row");
  });

  it("the BFF exposes trigger + cross-service lanes", () => {
    const bff = read(
      "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/AdminFacilityImportController.java",
    );
    expect(bff).toContain("/hpa-import/dry-run");
    expect(bff).toContain("/hpa-import/apply");
    expect(bff).toContain("/hpa-practitioner-onboarding");
    expect(bff).toContain("/hpa-geocode-review");
    expect(bff).toContain("hpaImportApply");
    expect(bff).toContain("hpaPractitionerOnboardingQueue");
    expect(bff).toContain("hpaGeocodeReviewQueue");
  });

  it("the route is registered (REGISTRY_ADMIN-gated)", () => {
    const route = ROUTES.find((r) => r.path === "/admin/hpa-enrichment");
    expect(route).toBeDefined();
    expect(route?.guard).toBe("role");
    expect(route?.requiredRole).toBe("REGISTRY_ADMIN");
    // Registry stays internally consistent (exact count is owned by routes.test.ts).
    expect(ROUTES).toHaveLength(EXPECTED_ROUTE_COUNT);
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
