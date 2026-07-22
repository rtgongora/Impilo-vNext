import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Golden thread for the ROM oversight UI (ROM-W9/W10). Two surfaces make governed regulatory
 * reporting + HPA cross-council oversight usable:
 *   - the org-scoped regulatory dashboards page (runs governed report definitions by class), and
 *   - the HPA oversight page (consolidated cross-council indicators + escalation grants).
 *
 * No ROUTES assertion — these routes are registered separately.
 */
const SRC = join(process.cwd(), "src");
const read = (p: string) => readFileSync(join(SRC, p), "utf8");

describe("ROM oversight UI", () => {
  const dashboardPage = read("app/work/regulatory/[orgId]/dashboard/page.tsx");
  const oversightPage = read("app/work/regulatory/hpa/oversight/page.tsx");

  it("the dashboard runs governed report definitions and offers the operational application queue", () => {
    expect(dashboardPage).toContain("/reports/");
    expect(dashboardPage).toContain("/run");
    expect(dashboardPage).toContain("reg.operational.application_queue");
  });

  it("the HPA oversight page runs the cross-council indicators report", () => {
    expect(oversightPage).toContain("reg.oversight.cross_council_indicators");
    expect(oversightPage).toContain("/reports/");
  });

  it("the HPA oversight page reads and records escalation grants", () => {
    expect(oversightPage).toContain("/oversight/grants");
    expect(oversightPage).toContain(
      'apiClient.post<unknown>("/internal/v1/regulatory/console/oversight/grants"',
    );
  });
});
