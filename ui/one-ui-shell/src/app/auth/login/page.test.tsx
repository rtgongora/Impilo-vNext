import type { ReactNode } from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import LoginPage from "./page";

const push = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/components/AuthLayout", () => ({
  AuthLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/hooks/queries/useAuth", () => ({
  useLogin: () => ({
    mutate: vi.fn(),
    isPending: false,
  }),
}));

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

  it("progresses to Step 2 credential resolution upon identifier submission", () => {
    render(<LoginPage />);

    const input = screen.getByLabelText(/Email, phone number, or Impilo ID/i);
    fireEvent.change(input, { target: { value: "mapfumo@mohcc.gov.zw" } });
    fireEvent.click(screen.getByRole("button", { name: /Continue/i }));

    expect(screen.getByLabelText("Password")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sign in" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Forgot password/i })).toHaveAttribute(
      "href",
      "/auth/forgot-password",
    );
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
