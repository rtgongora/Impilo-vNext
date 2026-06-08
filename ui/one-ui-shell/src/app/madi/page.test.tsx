import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import MadiHubPage from "./page";

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

describe("MadiHubPage", () => {
  it("renders the Madi hub with doctrine-aligned subtitle", () => {
    render(<MadiHubPage />);

    expect(screen.getByRole("heading", { level: 1, name: "Madi" })).toBeInTheDocument();
    expect(screen.getByText("Blood donation, transfusion safety and haemovigilance")).toBeInTheDocument();
  });

  it("shows work zone tiles with correct links", () => {
    render(<MadiHubPage />);

    expect(screen.getByRole("link", { name: /Operations Dashboard/i })).toHaveAttribute(
      "href",
      "/madi/dashboard",
    );
    expect(screen.getByRole("link", { name: /Donation Drives/i })).toHaveAttribute("href", "/madi/drives");
    expect(screen.getByRole("link", { name: /Order Blood/i })).toHaveAttribute("href", "/madi/orders");
    expect(screen.getByRole("link", { name: /Blood Logistics/i })).toHaveAttribute("href", "/madi/logistics");
  });

  it("shows donor life-zone entry points", () => {
    render(<MadiHubPage />);

    expect(screen.getByRole("link", { name: /My Donor Hub/i })).toHaveAttribute("href", "/madi/donor");
    expect(screen.getByRole("link", { name: /Become a Donor/i })).toHaveAttribute(
      "href",
      "/madi/donor/register",
    );
  });
});
