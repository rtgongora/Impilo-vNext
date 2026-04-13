import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import RegisterPage from "./page";

const push = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
}));

vi.mock("@/components/AuthLayout", () => ({
  AuthLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({
    setAuth: vi.fn(),
  }),
}));

vi.mock("@/hooks/useConsentStore", () => ({
  useConsentStore: () => ({
    acceptConsent: vi.fn(),
  }),
  CURRENT_CONSENT_VERSION: "2026-04-11",
}));

vi.mock("@/hooks/queries/usePolicyConsent", () => ({
  useAcceptPolicyConsent: () => ({
    mutate: vi.fn(),
    isPending: false,
  }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    post: vi.fn().mockResolvedValue({ data: { attributes: {} } }),
  },
  ApiResponse: {},
}));

describe("RegisterPage", () => {
  it("renders role selection step with all four account types", () => {
    render(<RegisterPage />);

    expect(screen.getByText("Create Account")).toBeInTheDocument();
    expect(screen.getByText("Choose your account type")).toBeInTheDocument();
    expect(screen.getByText("Citizen / Patient")).toBeInTheDocument();
    expect(screen.getByText("Clinician / Doctor")).toBeInTheDocument();
    expect(screen.getByText("Nurse")).toBeInTheDocument();
    expect(screen.getByText("Pharmacist")).toBeInTheDocument();
  });

  it("shows sign-in link for existing users", () => {
    render(<RegisterPage />);

    expect(screen.getByRole("link", { name: /Sign in/i })).toHaveAttribute(
      "href",
      "/auth/login",
    );
  });

  it("navigates to details step when a role is selected", async () => {
    const user = userEvent.setup();
    render(<RegisterPage />);

    await user.click(screen.getByText("Citizen / Patient"));

    expect(screen.getByText(/Registering as Citizen \/ Patient/)).toBeInTheDocument();
    expect(screen.getByText("First Name")).toBeInTheDocument();
    expect(screen.getByText("Last Name")).toBeInTheDocument();
    expect(screen.getByText("Email Address")).toBeInTheDocument();
    expect(screen.getByText("Password")).toBeInTheDocument();
    expect(screen.getByText("Confirm Password")).toBeInTheDocument();
  });
});
