import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import LabPage from "./page";

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

describe("LabPage", () => {
  it("renders laboratory hub with all section tiles", () => {
    render(<LabPage />);

    expect(screen.getByRole("heading", { level: 1, name: "Laboratory" })).toBeInTheDocument();
    expect(screen.getByText("Lab orders, results, worklists, and test catalog")).toBeInTheDocument();
  });

  it("links to all four lab sub-pages", () => {
    render(<LabPage />);

    expect(screen.getByText("Lab Worklist")).toBeInTheDocument();
    expect(screen.getByText("Results Review")).toBeInTheDocument();
    expect(screen.getByText("Test Catalog")).toBeInTheDocument();
    expect(screen.getByText("Reconciliation")).toBeInTheDocument();

    expect(screen.getByRole("link", { name: /Lab Worklist/i })).toHaveAttribute("href", "/lab/worklist");
    expect(screen.getByRole("link", { name: /Results Review/i })).toHaveAttribute("href", "/lab/results");
    expect(screen.getByRole("link", { name: /Test Catalog/i })).toHaveAttribute("href", "/lab/catalog");
    expect(screen.getByRole("link", { name: /Reconciliation/i })).toHaveAttribute("href", "/lab/reconciliation");
  });
});
