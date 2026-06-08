import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("emergency encounter golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("surfaces break-glass and ED chart handoffs on clinical emergency page", () => {
    const page = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/clinical/emergency/page.tsx"),
      "utf8",
    );
    expect(page).toContain("BreakGlassRequestPanel");
    expect(page).toContain("/internal/v1/emergency/");
    expect(page).toContain("ED workspace");
  });

  it("delegates provider break-glass through TSHEPO on web and mobile BFF", () => {
    const web = readFileSync(
      resolve(
        repoRoot,
        "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/TrustProviderController.java",
      ),
      "utf8",
    );
    const mobile = readFileSync(
      resolve(
        repoRoot,
        "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/MobileProviderController.java",
      ),
      "utf8",
    );
    expect(web).toContain("requestBreakGlass");
    expect(mobile).toContain("activateBreakGlass");
    expect(mobile).toContain("authzClient.requestBreakGlass");
  });

  it("links ED break-glass requests to admin review log", () => {
    const panel = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/components/trust/BreakGlassRequestPanel.tsx"),
      "utf8",
    );
    const adminPage = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/admin/break-glass/page.tsx"),
      "utf8",
    );
    expect(panel).toContain("/admin/break-glass?requestId=");
    expect(adminPage).toContain("useSearchParams");
    expect(adminPage).toContain("requestId");
  });
});
