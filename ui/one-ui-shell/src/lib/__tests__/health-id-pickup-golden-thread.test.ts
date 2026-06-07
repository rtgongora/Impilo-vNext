import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("health id pickup golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("wires pickup verify and confirm through BFF to VITO print routes", () => {
    const hooks = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useVitoPrint.ts"),
      "utf8",
    );
    const bff = readFileSync(
      resolve(
        repoRoot,
        "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/VitoPrintBffController.java",
      ),
      "utf8",
    );
    const vito = readFileSync(
      resolve(
        repoRoot,
        "services/vito-service/src/main/java/zw/gov/mohcc/impilo/vito/api/PrintJobController.java",
      ),
      "utf8",
    );

    expect(hooks).toContain("/internal/v1/vito/print/card/verify-pickup");
    expect(hooks).toContain("/internal/v1/vito/print/card/confirm-handover");
    expect(bff).toContain("/v1/print/card/verify-pickup");
    expect(bff).toContain("FacilityNameResolver");
    expect(vito).toContain("verifyPickup");
    expect(vito).toContain("confirmHandover");
  });
});
