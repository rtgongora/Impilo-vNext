/**
 * Golden thread for regulatory desk surfaces (CPD / restrictions / audit / stubs).
 */

import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { ROUTES } from "../routes";

const root = join(__dirname, "..", "..");
const read = (p: string) => readFileSync(join(root, p), "utf8");

describe("regulatory desk surfaces", () => {
  it("registers org-tree routes for cpd, restrictions and audit", () => {
    const paths = ROUTES.map((r) => r.path);
    expect(paths).toContain("/work/regulatory/[orgId]/cpd-review");
    expect(paths).toContain("/work/regulatory/[orgId]/restrictions");
    expect(paths).toContain("/work/regulatory/[orgId]/audit");
  });

  it("does not leave hub-reachable stubs on ScopedAdministrationSurface", () => {
    for (const stub of ["cpd-review", "restrictions", "audit", "cases", "licence-review"]) {
      const page = read(`app/work/regulators/[regulatorId]/${stub}/page.tsx`);
      expect(page).not.toContain("ScopedAdministrationSurface");
      expect(page).toContain("redirect");
    }
  });

  it("hub links CPD/Restrictions/Audit under the regulatory org tree", () => {
    const hub = read("app/work/regulatory/[orgId]/page.tsx");
    expect(hub).toContain("/cpd-review");
    expect(hub).toContain("/restrictions");
    expect(hub).toContain("/audit");
    expect(hub).not.toMatch(/href=\{`\/work\/regulators\/\$\{encodeURIComponent\(orgId\)\}\/cpd-review`\}/);
  });
});
