import type { ReactNode } from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import LoginPage from "./page";

const push = vi.fn();
const { beginOidcLogin } = vi.hoisted(() => ({ beginOidcLogin: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/components/AuthLayout", () => ({
  AuthLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/lib/auth/web-session", () => ({ beginOidcLogin }));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({
    isAuthenticated: false,
    setAuth: vi.fn(),
  }),
}));

vi.mock("@/hooks/useConsentStore", () => ({
  useConsentStore: () => ({
    acceptConsent: vi.fn(),
  }),
  CURRENT_CONSENT_VERSION: "2026-04-11",
}));

vi.mock("@/hooks/useWorkModeStore", () => ({
  useWorkModeStore: Object.assign(vi.fn(), {
    getState: () => ({ deriveFromRoles: vi.fn() }),
  }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get: vi.fn().mockResolvedValue({ data: [] }),
  },
}));

describe("LoginPage — Progressive Auth Scene", () => {
  it("renders Step 1 identifier input and Express Entry Intent Selector", () => {
    render(<LoginPage />);

    expect(screen.getByText("Sign in to Impilo")).toBeInTheDocument();
    expect(screen.getByTestId("express-intent-selector")).toBeInTheDocument();
    expect(screen.getByLabelText(/Email, phone number, or Impilo ID/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Continue/i })).toBeInTheDocument();
  });

  it("hands credentials to Keycloak without rendering a password input", () => {
    render(<LoginPage />);

    const input = screen.getByLabelText(/Email, phone number, or Impilo ID/i);
    fireEvent.change(input, { target: { value: "mapfumo@mohcc.gov.zw" } });
    fireEvent.click(screen.getByRole("button", { name: /Continue/i }));

    expect(screen.queryByLabelText("Password")).not.toBeInTheDocument();
    expect(beginOidcLogin).toHaveBeenCalledWith({
      returnTo: "/home",
      loginHint: "mapfumo@mohcc.gov.zw",
      requiredAcr: null,
    });
  });

  it("shows registration and guest continuation options", () => {
    render(<LoginPage />);

    expect(screen.getByRole("link", { name: /Create an account/i })).toHaveAttribute(
      "href",
      "/auth/register/contact",
    );
    // Guest continuation must land on a public path (never the authed /home) and,
    // with no returnTo, defaults to the public landing so the guest keeps a public journey.
    expect(screen.getByRole("link", { name: /Continue as guest/i })).toHaveAttribute(
      "href",
      "/",
    );
  });
});
