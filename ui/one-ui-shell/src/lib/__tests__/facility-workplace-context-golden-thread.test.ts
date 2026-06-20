import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("facility-workplace-context cross-service golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("facility selection UI calls facilities BFF", () => {
    const page = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/facility/page.tsx"), "utf8");
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useFacilities.ts"), "utf8");
    expect(page).toContain("useFacilities");
    expect(hook).toContain("/internal/v1/facilities");
  });

  it("workplace hub composes facility context", () => {
    const hub = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/components/home/WorkplaceSelectionHub.tsx"), "utf8");
    expect(hub).toContain("FacilityResource");
  });

  it("BFF facility controller proxies TUSO registry", () => {
    const controller = readFileSync(
      resolve(repoRoot, "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/FacilityController.java"),
      "utf8",
    );
    expect(controller).toContain("/internal/v1/facilities");
  });
});
