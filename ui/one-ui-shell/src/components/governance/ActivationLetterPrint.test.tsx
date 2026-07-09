import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { ActivationLetterPrint } from "./ActivationLetterPrint";

describe("ActivationLetterPrint", () => {
  it("renders provider id, activation link, code, expiry and helpdesk", () => {
    render(
      <ActivationLetterPrint
        displayName="Rudo Gwena"
        providerId="PROV-ZW-00009"
        activationUrl="https://impilo.mohcc.gov.zw/auth/login"
        activationCode="493-201"
        expiresAt="2026-07-16"
        facilityName="Parirenyatwa Group of Hospitals"
      />,
    );
    expect(screen.getByTestId("letter-provider-id").textContent).toBe("PROV-ZW-00009");
    expect(screen.getByTestId("letter-activation-code").textContent).toBe("493-201");
    expect(screen.getByText(/impilo\.mohcc\.gov\.zw/)).toBeTruthy();
    expect(screen.getByText(/expires on 2026-07-16/)).toBeTruthy();
    expect(screen.getByText(/helpdesk@mohcc\.gov\.zw/)).toBeTruthy();
  });

  it("NEVER contains password-like content — the safety invariant", () => {
    const { container } = render(
      <ActivationLetterPrint displayName="Test" activationUrl="https://impilo.mohcc.gov.zw" />,
    );
    const text = container.textContent ?? "";
    // The only permitted mention is the explicit anti-phishing guidance line.
    const withoutGuidance = text
      .replace("During activation you will set your own password and confirm your identity.", "")
      .replace("Nobody from the Ministry or Impilo will ever ask you for your password.", "");
    expect(withoutGuidance.toLowerCase()).not.toContain("password");
    expect(text.toLowerCase()).not.toContain("diagnosis");
  });
});
