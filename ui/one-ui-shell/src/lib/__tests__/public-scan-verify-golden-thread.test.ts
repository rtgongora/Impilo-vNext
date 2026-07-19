import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

/**
 * PJ6/D-P8 + FJ QR/D-L7 golden thread: the public badge- and facility-
 * credential-scan pages are wired page → BFF public passthrough → varapi/tuso
 * public controller, the QR travels in the body (never the URL), and the lane
 * is registered as an anonymous public route in the ADR + SecurityConfig.
 */
describe("public scan verify golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");
  const read = (p: string) => readFileSync(resolve(repoRoot, p), "utf8");

  it("badge page posts the QR in the body to the public gateway lane", () => {
    const page = read("ui/one-ui-shell/src/app/verify/badge/page.tsx");
    expect(page).toContain("/internal/v1/public/gateway/practitioners/badge-scan");
    expect(page).toContain("apiClient.post");
    expect(page).toContain("NOT_RECOGNISED");
    // No login link on a public verify page (verify ≠ claim ≠ sign-in).
    expect(page).not.toContain("/auth/login");
  });

  it("facility-credential page posts the QR to the public cert lane", () => {
    const page = read("ui/one-ui-shell/src/app/verify/facility-credential/page.tsx");
    expect(page).toContain("/internal/v1/public/facility-certificates/credential-scan");
    expect(page).toContain("apiClient.post");
    expect(page).toContain("NOT_RECOGNISED");
  });

  it("BFF passthroughs forward to the varapi/tuso public scan endpoints", () => {
    const practitioner = read(
      "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/PublicGatewayPractitionerBffController.java",
    );
    expect(practitioner).toContain("/badge-scan");
    expect(practitioner).toContain("publicBadgeScan");

    const cert = read(
      "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/PublicCertificateVerificationBffController.java",
    );
    expect(cert).toContain("/credential-scan");
    expect(cert).toContain("publicCredentialScan");
  });

  it("the two public scan routes are permitAll + registered in the ADR", () => {
    const sec = read(
      "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/config/SecurityConfig.java",
    );
    expect(sec).toContain("/internal/v1/public/gateway/practitioners/badge-scan");
    expect(sec).toContain("/internal/v1/public/facility-certificates/credential-scan");

    const adr = read("docs/architecture/gateway-public-lane-security-adr.md");
    expect(adr).toContain("badge-scan");
    expect(adr).toContain("credential-scan");
  });
});
