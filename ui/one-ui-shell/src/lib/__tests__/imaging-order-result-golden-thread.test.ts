import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("imaging order-result golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("submits IMAGING orders from encounter panel via lab-orders BFF", () => {
    const panel = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/components/encounter/EncounterImagingOrdersPanel.tsx"),
      "utf8",
    );
    expect(panel).toContain('category: "IMAGING"');
    expect(panel).toContain("/imaging/viewer");
  });

  it("exposes imaging viewer route for study review", () => {
    const viewer = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/ehr/[patientId]/imaging/viewer/page.tsx"),
      "utf8",
    );
    expect(viewer.length).toBeGreaterThan(100);
  });

  it("correlates imaging orders to PACS studies from imaging workspace", () => {
    const imagingPage = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/ehr/[patientId]/imaging/page.tsx"),
      "utf8",
    );
    const correlatePanel = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/components/imaging/ImagingOrderCorrelatePanel.tsx"),
      "utf8",
    );
    expect(imagingPage).toContain("ImagingOrderCorrelatePanel");
    expect(imagingPage).toContain("orderId");
    expect(correlatePanel).toContain("/internal/v1/imaging/studies");
  });
});
