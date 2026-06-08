import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("telemedicine encounter golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("session workspace loads sovereign teleconsult session without local shell fallback", () => {
    const page = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/telemedicine/session/[sessionId]/page.tsx"),
      "utf8",
    );
    expect(page).toContain("/internal/v1/teleconsult/sessions/");
    expect(page).not.toContain('status: "IN_SESSION"');
    expect(page).toContain("RTC");
  });

  it("BFF teleconsult controller supports create, consent, submit, and complete", () => {
    const controller = readFileSync(
      resolve(
        repoRoot,
        "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/TeleconsultController.java",
      ),
      "utf8",
    );
    expect(controller).toContain("/internal/v1/teleconsult");
    expect(controller).toContain("/sessions");
    expect(controller).toContain("consent");
    expect(controller).toContain("submit");
    expect(controller).toContain("complete");
  });
});
