import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("citizen-onboarding golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("registration enters the verified-contact journey without collecting a password", () => {
    const page = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/auth/register/page.tsx"), "utf8");
    const contact = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/auth/register/contact/page.tsx"), "utf8");
    expect(page).toContain("/auth/register/contact");
    expect(page).toContain("window.location.search");
    expect(page).not.toContain('type="password"');
    expect(contact).toContain("useRequestContactOtp");
    expect(contact).toContain("useVerifyContactOtp");
    expect(contact).toContain('purpose: "REGISTER"');
  });

  it("BFF controller exposes journey endpoints", () => {
    const controller = readFileSync(resolve(repoRoot, "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/AuthSessionController.java"), "utf8");
    expect(controller).toContain("/internal/v1");
    expect(controller).toContain("register");
  });
});
