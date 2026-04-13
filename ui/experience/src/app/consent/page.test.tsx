import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ConsentPage from "./page";

const push = vi.fn();
const replace = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, replace }),
  useSearchParams: () => ({
    get: (key: string) => {
      if (key === "returnTo") return "/home";
      if (key === "channel") return null;
      return null;
    },
  }),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({
    user: {
      id: "user-1",
      email: "test@impilo.io",
      displayName: "Test User",
      roles: ["CITIZEN"],
      actorType: "CITIZEN",
    },
    clearAuth: vi.fn(),
  }),
}));

vi.mock("@/hooks/useConsentStore", () => ({
  useConsentStore: () => ({
    acceptConsent: vi.fn(),
  }),
  CURRENT_CONSENT_VERSION: "2026-04-11",
}));

vi.mock("@/hooks/queries/useAuth", () => ({
  useLogout: () => ({
    mutate: vi.fn(),
    isPending: false,
  }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    post: vi.fn().mockResolvedValue({ data: {} }),
  },
}));

describe("ConsentPage", () => {
  it("renders the standard consent interstitial with privacy and terms checkboxes", () => {
    render(<ConsentPage />);

    expect(screen.getByText("Review Our Policies")).toBeInTheDocument();
    expect(screen.getByText("Privacy Policy")).toBeInTheDocument();
    expect(screen.getByText("Terms of Use")).toBeInTheDocument();
    expect(screen.getByText(/Accept and Continue/)).toBeInTheDocument();
    expect(screen.getByText(/Decline and Sign Out/)).toBeInTheDocument();
  });

  it("shows the signed-in user email", () => {
    render(<ConsentPage />);

    expect(screen.getByText(/Signed in as test@impilo.io/)).toBeInTheDocument();
  });

  it("shows policy version", () => {
    render(<ConsentPage />);

    expect(screen.getByText(/Policy version: 2026-04-11/)).toBeInTheDocument();
  });

  it("disables the accept button until both checkboxes are checked", () => {
    render(<ConsentPage />);

    const acceptButton = screen.getByRole("button", { name: /Accept and Continue/ });
    expect(acceptButton).toBeDisabled();
  });
});
