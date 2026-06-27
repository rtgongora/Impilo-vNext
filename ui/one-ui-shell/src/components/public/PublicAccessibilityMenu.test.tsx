import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, beforeEach } from "vitest";
import { PublicAccessibilityMenu } from "./PublicAccessibilityMenu";

describe("PublicAccessibilityMenu (G-CZO-08, Persona E)", () => {
  beforeEach(() => {
    sessionStorage.clear();
    document.documentElement.className = "";
  });

  it("exposes the accessibility toggles to a guest (no login required)", () => {
    render(<PublicAccessibilityMenu />);
    expect(screen.getByText("High Contrast")).toBeInTheDocument();
    expect(screen.getByText("Large Text")).toBeInTheDocument();
    expect(screen.getByText("Low Bandwidth")).toBeInTheDocument();
    expect(screen.getByText("Simple Language")).toBeInTheDocument();
  });

  it("toggling high contrast applies the global CSS class and reflects pressed state", () => {
    render(<PublicAccessibilityMenu />);
    const button = screen.getByRole("button", { name: /high contrast/i });
    expect(button).toHaveAttribute("aria-pressed", "false");

    fireEvent.click(button);

    expect(button).toHaveAttribute("aria-pressed", "true");
    expect(document.documentElement.classList.contains("impilo-high-contrast")).toBe(true);
  });
});
