import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { EmergencyHelpButton } from "./EmergencyHelpButton";

describe("EmergencyHelpButton", () => {
  it("is a clearly labelled link to /welcome/emergency (text, never icon-only)", () => {
    render(<EmergencyHelpButton />);
    const link = screen.getByTestId("emergency-help-button");
    expect(link).toHaveAttribute("href", "/welcome/emergency");
    expect(link).toHaveTextContent("Emergency Help");
    expect(link).toHaveAccessibleName(/emergency help/i);
  });

  it("keeps a large touch target and stays fixed to the lower corner", () => {
    render(<EmergencyHelpButton />);
    const link = screen.getByTestId("emergency-help-button");
    expect(link.className).toContain("min-h-[44px]");
    expect(link.className).toContain("fixed");
    expect(link.className).toContain("bottom-3");
  });

  it("raised variant lifts above bottom chrome such as the taskbar", () => {
    render(<EmergencyHelpButton raised />);
    const link = screen.getByTestId("emergency-help-button");
    expect(link.className).toContain("bottom-16");
    expect(link.className).not.toContain("bottom-3");
  });

  it("has no auth, coverage, or payment coupling — renders with zero providers", () => {
    // Rendering outside every provider/store proves there is no gating dependency
    // in its path (doctrine §7.2: emergency access is never blocked).
    expect(() => render(<EmergencyHelpButton />)).not.toThrow();
  });
});
