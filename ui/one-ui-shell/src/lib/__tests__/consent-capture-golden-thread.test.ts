import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("consent capture golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("mvumo registry admin uses typed BFF template contract", () => {
    const page = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/registry/mvumo/page.tsx"),
      "utf8",
    );
    expect(page).toContain("/internal/v1/mvumo-admin/templates");
    expect(page).not.toContain("coming soon");
  });

  it("procedure episode consent lifecycle proxies mvumo through BFF", () => {
    const controller = readFileSync(
      resolve(
        repoRoot,
        "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/ProcedureWorkflowController.java",
      ),
      "utf8",
    );
    const capture = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/components/clinical/ProcedureConsentCapture.tsx"),
      "utf8",
    );
    expect(controller).toContain("/consent/explanation");
    expect(controller).toContain("/consent/verify-identity");
    expect(controller).toContain("/consent/grant");
    expect(controller).toContain("/consent/remote-session");
    expect(capture).toContain("Grant consent (MVUMO → Tshepo)");
    expect(capture).not.toContain("consentVerified: true");
  });

  it("teleconsult consent step proxies mvumo through BFF", () => {
    const controller = readFileSync(
      resolve(
        repoRoot,
        "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/TeleconsultController.java",
      ),
      "utf8",
    );
    const mvumoAdmin = readFileSync(
      resolve(
        repoRoot,
        "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/MvumoAdminController.java",
      ),
      "utf8",
    );
    expect(controller).toContain("/consent");
    expect(mvumoAdmin).toContain("/internal/v1/mvumo-admin");
    expect(mvumoAdmin).toContain("/templates");
  });
});
