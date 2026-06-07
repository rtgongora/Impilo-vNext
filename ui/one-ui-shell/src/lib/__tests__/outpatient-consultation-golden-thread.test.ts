import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("outpatient consultation golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("wires discharge and imaging panels on the encounter page", () => {
    const page = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/ehr/[patientId]/encounter/[encounterId]/page.tsx"),
      "utf8",
    );
    expect(page).toContain("EncounterDischargePanel");
    expect(page).toContain("EncounterImagingOrdersPanel");
  });

  it("posts discharge through BFF with core-transaction invalidation", () => {
    const hook = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useDischarge.ts"),
      "utf8",
    );
    expect(hook).toContain("/internal/v1/encounters/${data.encounterId}/discharge");
    expect(hook).toContain('queryKey: ["core-transaction"]');
  });

  it("forwards discharge correlation meta from BFF", () => {
    const controller = readFileSync(
      resolve(
        repoRoot,
        "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/EncounterController.java",
      ),
      "utf8",
    );
    expect(controller).toContain("core_transaction_id");
    expect(controller).toContain("startDischarge");
  });

  it("routes ADMIT discharge to inpatient admission handoff", () => {
    const panel = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/components/encounter/EncounterDischargePanel.tsx"),
      "utf8",
    );
    const admissionsPage = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/clinical/inpatient/admissions/page.tsx"),
      "utf8",
    );
    expect(panel).toContain('dischargeType === "ADMIT"');
    expect(panel).toContain("/clinical/inpatient/admissions");
    expect(admissionsPage).toContain("InpatientAdmissionHandoffBanner");
  });
});
