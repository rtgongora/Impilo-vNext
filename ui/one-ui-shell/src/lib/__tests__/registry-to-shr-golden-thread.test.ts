import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("registry-to-shr cross-service golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("registry UI resolves identity via BFF", () => {
    const page = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/registry/page.tsx"), "utf8");
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useIdentityOperations.ts"), "utf8");
    expect(page).toContain("useIdentityOperations");
    expect(hook).toContain("/internal/v1/identity/patient/register");
  });

  it("FHIR interop surface reaches butano-fhir BFF", () => {
    const page = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/operations/butano/page.tsx"), "utf8");
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useFhirInterop.ts"), "utf8");
    expect(page).toContain("useFhirCapabilityStatement");
    expect(hook).toContain("/internal/v1/fhir");
  });

  it("patient queue hooks reach PCT-aligned BFF", () => {
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/usePatients.ts"), "utf8");
    const controller = readFileSync(
      resolve(repoRoot, "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/PatientController.java"),
      "utf8",
    );
    expect(hook).toContain("/internal/v1/patients");
    expect(controller).toContain("/internal/v1/patients");
  });
});
