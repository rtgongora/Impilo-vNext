import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("wellness journey golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("routes page uses wellness discover API only", () => {
    const page = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/wellness/routes/page.tsx"),
      "utf8",
    );
    expect(page).toContain("fetchWellnessDiscoverServices");
    expect(page).not.toContain("coming soon");
  });

  it("logs discover visits through wellness activity mutation", () => {
    const page = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/wellness/routes/page.tsx"),
      "utf8",
    );
    expect(page).toContain("useLogWellnessActivity");
    expect(page).toContain("Log planned visit");
  });
});
