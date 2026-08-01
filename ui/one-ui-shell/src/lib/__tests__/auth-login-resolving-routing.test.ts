import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("Keycloak login preserves the post-login destination", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  // Biometric sign-in is an intentional "not available yet" stub (G-CZO-14): it does NOT
  // sign the user in and therefore does not route through /auth/resolving.
  it("provider-id login gives Keycloak the shared resolving destination", () => {
    const source = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/auth/login/provider-id/page.tsx"), "utf8");
    expect(source).toContain("beginOidcLogin");
    expect(source).toContain("returnTo: buildPostLoginResolvingPath(returnTo)");
    expect(source).toContain('requiredAcr: "urn:impilo:aal2"');
  });

  it("preserves returnTo in resolving path for provider-id login", () => {
    const source = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/auth/login/provider-id/page.tsx"),
      "utf8",
    );
    expect(source).toContain('searchParams.get("returnTo")');
  });

  it("legacy MFA links hand the preserved destination to Keycloak", () => {
    const source = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/auth/mfa/page.tsx"), "utf8");
    expect(source).toContain("beginOidcLogin");
    expect(source).toContain('searchParams.get("returnTo") ?? "/home"');
    expect(source).toContain('requiredAcr: "urn:impilo:aal2"');
    expect(source).not.toContain("apiClient");
  });
});
