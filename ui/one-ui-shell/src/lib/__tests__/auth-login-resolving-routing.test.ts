import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("auth login success routes through /auth/resolving", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  // Biometric sign-in is an intentional "not available yet" stub (G-CZO-14): it does NOT
  // sign the user in and therefore does not route through /auth/resolving.
  const authPages = [
    "ui/one-ui-shell/src/app/auth/login/provider-id/page.tsx",
    "ui/one-ui-shell/src/app/auth/mfa/page.tsx",
  ] as const;

  it.each(authPages)("%s pushes buildPostLoginResolvingPath after success", (relativePath) => {
    const source = readFileSync(resolve(repoRoot, relativePath), "utf8");
    expect(source).toContain("buildPostLoginResolvingPath");
    expect(source).toContain("router.push(buildPostLoginResolvingPath(returnTo))");
  });

  it("preserves returnTo in resolving path for provider-id login", () => {
    const source = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/auth/login/provider-id/page.tsx"),
      "utf8",
    );
    expect(source).toContain('searchParams.get("returnTo")');
  });

  it("MFA page imports resolver from shared module", () => {
    for (const page of ["auth/mfa/page.tsx"] as const) {
      const source = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app", page), "utf8");
      expect(source).toContain('@/lib/resolve-post-login-destination');
    }
  });
});
