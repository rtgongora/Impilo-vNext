import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

/**
 * Facility completeness + data-gap resolution golden thread (closes the V036 UI gap):
 * the /facility/[id]/trust page → useFacilityLifecycle completeness/data-gap hooks →
 * BFF FacilityLifecycleController → tuso FacilityDataGapController (facility_completeness_dimension
 * + facility_data_gap_task). A facility admin can see per-dimension completeness and
 * resolve the actionable gaps.
 */
describe("facility completeness + data-gap golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");
  const read = (p: string) => readFileSync(resolve(repoRoot, p), "utf8");

  it("the trust page shows completeness dimensions + resolvable data gaps", () => {
    const page = read("ui/one-ui-shell/src/app/facility/[id]/trust/page.tsx");
    expect(page).toContain("useFacilityCompletenessDimensions");
    expect(page).toContain("useFacilityDataGaps");
    expect(page).toContain("useResolveDataGap");
    expect(page).toContain("facility-completeness");
    expect(page).toContain("data-gap-row");
    expect(page).toContain("Mark resolved");
  });

  it("the hook targets the BFF completeness/data-gap lanes", () => {
    const hook = read("ui/one-ui-shell/src/hooks/queries/useFacilityLifecycle.ts");
    expect(hook).toContain("/completeness");
    expect(hook).toContain("/data-gaps");
    expect(hook).toContain("/resolve");
  });

  it("the BFF proxies to the tuso data-gap controller", () => {
    const bff = read(
      "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/FacilityLifecycleController.java",
    );
    expect(bff).toContain("/{facilityUuid}/completeness");
    expect(bff).toContain("/{facilityUuid}/data-gaps");
    expect(bff).toContain("resolveDataGap");
  });

  it("the tuso controller reads V036 tables + resolves with an audit note", () => {
    const ctrl = read(
      "services/tuso-service/src/main/java/zw/gov/mohcc/impilo/tuso/api/controller/FacilityDataGapController.java",
    );
    expect(ctrl).toContain("facility_completeness_dimension");
    expect(ctrl).toContain("facility_data_gap_task");
    expect(ctrl).toContain("resolution_history");
    // Path carries /facility-data-gaps so the V042 tshepo seed gates it.
    expect(ctrl).toContain("facility-data-gaps");
  });
});
