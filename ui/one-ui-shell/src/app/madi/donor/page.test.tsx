import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import DonorHubPage from "./page";

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({
    children,
    title,
    subtitle,
  }: {
    children: ReactNode;
    title: string;
    subtitle?: string;
  }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: (state: { user: { id: string; healthId?: string } | null }) => unknown) =>
    selector({ user: { id: "health-1", healthId: "cpid-1" } }),
}));

vi.mock("@/hooks/queries/useMadi", () => ({
  useDonorByPerson: () => ({
    data: null,
    isPending: false,
    isError: false,
  }),
  useDonorNextEligibility: () => ({ data: null }),
}));

describe("DonorHubPage", () => {
  it("renders the donor hub with privacy-safe subtitle", () => {
    render(<DonorHubPage />);

    expect(screen.getByRole("heading", { level: 1, name: "My Donor Hub" })).toBeInTheDocument();
    expect(
      screen.getByText("Give blood safely — your voluntary contribution saves lives"),
    ).toBeInTheDocument();
  });

  it("prompts unregistered users to become a donor", () => {
    render(<DonorHubPage />);

    expect(screen.getByText(/not registered as a donor/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Become a blood donor/i })).toHaveAttribute(
      "href",
      "/madi/donor/register",
    );
  });

  it("lists donor navigation links", () => {
    render(<DonorHubPage />);

    expect(screen.getByRole("link", { name: /Find a drive/i })).toHaveAttribute(
      "href",
      "/madi/donor/drives",
    );
    expect(screen.getByRole("link", { name: /Donation history/i })).toHaveAttribute(
      "href",
      "/madi/donor/history",
    );
  });
});
