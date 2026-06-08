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
    expect(screen).toContain("submitAssessmentAttempt");
    expect(screen).toContain("issueCertificate");
    const service = readFileSync(
      resolve(repoRoot, "apps/mobile/provider-app/src/services/fundoLearningService.ts"),
      "utf8",
    );
    expect(service).toContain("/internal/v1/learning/v11");
    expect(service).toContain("submitAssessmentAttempt");
  });

  it("Playwright fundo journey spec covers catalogue through CPD evidence", () => {
    const spec = readFileSync(resolve(repoRoot, "ui/one-ui-shell/e2e/fundo-learning-flow.spec.ts"), "utf8");
    expect(spec).toContain("full learner journey");
    expect(spec).toContain("fundo-assessment-submit");
    expect(spec).toContain("/learning/cpd");
  });

  it("compose e2e proves real learning-service stack without mocks", () => {
    const spec = readFileSync(resolve(repoRoot, "ui/one-ui-shell/e2e/fundo-learning-compose.spec.ts"), "utf8");
    expect(spec).toContain("no mocks");
    expect(spec).toContain("/learning/v11/catalog");
  });

  it("content formats doctrine documents governed upload types", () => {
    const doc = readFileSync(resolve(repoRoot, "docs/learning/FUNDO_CONTENT_FORMATS.md"), "utf8");
    expect(doc).toContain("PRACTICAL_TASK");
    expect(doc).toContain("verificationDigest");
  });
});
