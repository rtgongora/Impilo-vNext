import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("chronic-care golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("UI route wires bounded-context client", () => {
    const page = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/ehr/[patientId]/care-plans/page.tsx"), "utf8");
    expect(page).toContain("useCarePlans");
    expect(page).toContain("useCreateCarePlan");
  });

  it("BFF controller exposes journey endpoints", () => {
    const controller = readFileSync(resolve(repoRoot, "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/CareEmergencyInpatientController.java"), "utf8");
    expect(controller).toContain("care-plans");
    expect(controller).toContain("CarePlan");
  });
});
