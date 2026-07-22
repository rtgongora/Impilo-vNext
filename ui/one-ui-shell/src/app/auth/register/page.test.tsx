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

vi.mock("@/hooks/queries/useIdentityAssurance", () => ({
  useRegistrationReadiness: () => ({
    data: { data: { attributes: { ready: true } } },
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    post: vi.fn().mockResolvedValue({ data: { attributes: {} } }),
  },
  ApiResponse: {},
}));

describe("RegisterPage", () => {
  it("renders the person-first registration form", () => {
    render(<RegisterPage />);

    expect(screen.getByText("Sign up for Impilo")).toBeInTheDocument();
    expect(screen.getByText("Create your personal Impilo account")).toBeInTheDocument();
    expect(screen.getByText("Full name")).toBeInTheDocument();
    expect(screen.getByText("Email or phone")).toBeInTheDocument();
    expect(screen.getByText("Password")).toBeInTheDocument();
    expect(screen.getByText("Confirm password")).toBeInTheDocument();
    expect(screen.getByText("Do you have an Impilo ID?")).toBeInTheDocument();
  });

  it("shows sign-in link for existing users", () => {
    render(<RegisterPage />);

    expect(screen.getByRole("link", { name: /Sign in/i })).toHaveAttribute(
      "href",
      "/auth/login",
    );
  });

  it("requires privacy and terms before submit is enabled", async () => {
    const user = userEvent.setup();
    render(<RegisterPage />);

    const submit = screen.getByRole("button", { name: /Create Account/i });
    expect(submit).toBeDisabled();

    await user.type(screen.getByPlaceholderText("Your full name"), "Tariro Moyo");
    await user.type(screen.getByPlaceholderText("you@example.com or +263..."), "tariro@example.com");
    await user.type(screen.getByPlaceholderText("At least 12 characters with upper, lower, digit, and symbol"), "SecurePass123!");
    await user.type(screen.getByPlaceholderText("Repeat password"), "SecurePass123!");

    const privacy = screen.getByRole("checkbox", { name: /Privacy Policy/i });
    const terms = screen.getByRole("checkbox", { name: /Terms of Use/i });
    await user.click(privacy);
    await user.click(terms);

    expect(submit).not.toBeDisabled();
  });
});
