import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("citizen monitoring golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("routes devices, readings, and alerts through wellness/monitoring BFF paths", () => {
    const api = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/lib/citizen-monitoring-api.ts"),
      "utf8",
    );
    const readings = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/monitoring/readings/page.tsx"),
      "utf8",
    );
    const alerts = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/monitoring/alerts/page.tsx"),
      "utf8",
    );

    expect(api).toContain("/internal/v1/mobile/citizen/monitoring");
    expect(api).toContain("ingestMonitoringReadings");
    expect(api).toContain("${BASE}/readings");
    expect(api).toContain("/internal/v1/mobile/citizen/wellness/vitals");
    expect(api).toContain("/internal/v1/wellness/personal-data/remote-alerts");
    expect(readings).toContain("useMonitoringReadings");
    expect(alerts).toContain("useMonitoringAlerts");
    const devices = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/monitoring/devices/page.tsx"),
      "utf8",
    );
    expect(devices).toContain("Sync reading");
  });
});
