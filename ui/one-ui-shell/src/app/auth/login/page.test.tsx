import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
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

describe("LoginPage", () => {
  it("renders the sign-in form with email and password fields", () => {
    render(<LoginPage />);

    expect(screen.getByText("Welcome back")).toBeInTheDocument();
    expect(screen.getByText("Sign in to continue to Impilo")).toBeInTheDocument();
    expect(screen.getByLabelText(/Email or phone/i)).toBeInTheDocument();
    expect(screen.getByLabelText("Password")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sign In" })).toBeInTheDocument();
  });

  it("shows alternate sign-in methods and registration link", () => {
    render(<LoginPage />);

    expect(screen.getByText("Other sign-in methods")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /^Health ID$/i })).toHaveAttribute(
      "href",
      "/auth/login/provider-id",
    );
    expect(screen.getByRole("link", { name: /Biometric/i })).toHaveAttribute(
      "href",
      "/auth/login/biometric",
    );
    expect(screen.getByRole("link", { name: /Create an account/i })).toHaveAttribute(
      "href",
      "/auth/register",
    );
  });

  it("shows forgot password link", () => {
    render(<LoginPage />);

    expect(screen.getByRole("link", { name: /Forgot password/i })).toHaveAttribute(
      "href",
      "/auth/forgot-password",
    );
  });
});
