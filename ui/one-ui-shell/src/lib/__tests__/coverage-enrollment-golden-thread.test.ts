import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("coverage enrollment golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("citizen enroll route uses coverage hooks and eligibility command", () => {
    const page = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/coverage/enroll/page.tsx"),
      "utf8",
    );
    const hooks = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useCoverage.ts"),
      "utf8",
    );
    expect(page).toContain("useCoveragePlans");
    expect(page).toContain("useEnrollCoverageMember");
    expect(page).toContain("useRunCoverageCommand");
    expect(hooks).toContain("/internal/v1/coverage/members");
    expect(hooks).toContain("/internal/v1/coverage/eligibility/enrollment");
  });

  it("BFF coverage controller exposes enrollment endpoints", () => {
    const controller = readFileSync(
      resolve(
        repoRoot,
        "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/CoverageController.java",
      ),
      "utf8",
    );
    expect(controller).toContain("/internal/v1/coverage");
    expect(controller).toContain("/members");
    expect(controller).toContain("/eligibility");
  });
});
