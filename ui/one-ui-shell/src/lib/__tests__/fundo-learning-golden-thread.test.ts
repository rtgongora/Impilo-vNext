import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("fundo-learning golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("UI route wires bounded-context client", () => {
    const page = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/learning/page.tsx"), "utf8");
    expect(page).toContain("useFundoMyLearning");
    expect(page).toContain("FundoLearningOrchestrationRail");
  });

  it("hook calls BFF sovereign proxy", () => {
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useFundoLms.ts"), "utf8");
    expect(hook).toContain("/internal/v1/learning");
  });

  it("BFF controller exposes journey endpoints", () => {
    const controller = readFileSync(resolve(repoRoot, "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/LearningController.java"), "utf8");
    expect(controller).toContain("/internal/v1/learning");
  });

  it("provider mobile FundoLearningShellScreen uses learning BFF", () => {
    const screen = readFileSync(
      resolve(repoRoot, "apps/mobile/provider-app/src/screens/provider/FundoLearningShellScreen.tsx"),
      "utf8",
    );
    expect(screen).toContain("fundoLearningService");
    expect(screen).toContain("fetchMyLearning");
    const service = readFileSync(
      resolve(repoRoot, "apps/mobile/provider-app/src/services/fundoLearningService.ts"),
      "utf8",
    );
    expect(service).toContain("/internal/v1/learning/v11");
  });
});
