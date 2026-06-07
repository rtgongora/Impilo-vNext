import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("prescription dispense golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("dispenses from web pharmacy workspace through BFF", () => {
    const page = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/pharmacy/dispense/page.tsx"),
      "utf8",
    );
    expect(page).toContain("/internal/v1/pharmacy/dispense");
  });

  it("routes mobile provider pending list through pharmacy worklist BFF", () => {
    const controller = readFileSync(
      resolve(
        repoRoot,
        "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/MobileProviderExtendedController.java",
      ),
      "utf8",
    );
    const mobileService = readFileSync(
      resolve(repoRoot, "apps/mobile/provider-app/src/services/queueService.ts"),
      "utf8",
    );
    expect(controller).toContain("pharmacyClient.getWorklist");
    expect(mobileService).toContain("/pharmacy/pending");
  });
});
