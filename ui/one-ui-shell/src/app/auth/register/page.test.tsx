import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import RegisterPage from "./page";

vi.mock("@/components/AuthLayout", () => ({
  AuthLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

describe("RegisterPage", () => {
  it("preserves the journey while converging on password-free Impilo registration", () => {
    const replace = vi.fn();
    Object.defineProperty(window, "location", {
      value: { search: "?returnTo=%2Fhome&gwi=opaque", replace },
      configurable: true,
    });
    render(<RegisterPage />);
    expect(replace).toHaveBeenCalledWith("/auth/register/contact?returnTo=%2Fhome&gwi=opaque");
    expect(screen.getByText(/Opening secure account creation/i)).toBeInTheDocument();
  });
});
