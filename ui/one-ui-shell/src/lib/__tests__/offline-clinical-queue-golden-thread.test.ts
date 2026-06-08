import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("offline-clinical-queue golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("UI route wires bounded-context client", () => {
    const page = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/clinical-tools/page.tsx"), "utf8");
    expect(page).toContain("offline");
  });

  it("BFF controller exposes journey endpoints", () => {
    const controller = readFileSync(resolve(repoRoot, "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/mobile/MobileOfflineController.java"), "utf8");
    expect(controller).toContain("/internal/v1/mobile/provider/offline");
  });
});
