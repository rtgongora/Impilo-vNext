import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("maps-geospatial-logistics cross-service golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("Nhume map UI uses nhume BFF hooks", () => {
    const page = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/nhume/map/page.tsx"), "utf8");
    const nhume = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/lib/nhume.ts"), "utf8");
    expect(page).toContain("useNhumeMapToken");
    expect(nhume).toContain("/internal/v1/nhume");
  });

  it("dispatch operations UI uses dispatch BFF", () => {
    const page = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/operations/dispatch/page.tsx"), "utf8");
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useDispatchOps.ts"), "utf8");
    expect(page).toContain("useDispatchDeliveries");
    expect(hook).toContain("/internal/v1/dispatch");
  });

  it("unified logistics panel composes nhume fleet and deliveries", () => {
    const panel = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/components/operations/UnifiedLogisticsMapPanel.tsx"),
      "utf8",
    );
    expect(panel).toContain("useNhumeDeliveries");
    expect(panel).toContain("useNhumeFleet");
  });
});
