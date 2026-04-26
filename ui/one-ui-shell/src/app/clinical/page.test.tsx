import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ClinicalHubPage from "./page";

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

describe("ClinicalHubPage", () => {
  it("renders the clinical care hub with all module tiles", () => {
    render(<ClinicalHubPage />);

    expect(screen.getByRole("heading", { level: 1, name: "Clinical Care" })).toBeInTheDocument();
    expect(screen.getByText("Patient encounters, assessments, and care delivery")).toBeInTheDocument();
  });

  it("shows all 11 clinical module tiles with correct links", () => {
    render(<ClinicalHubPage />);

    expect(screen.getByText("My Dashboard")).toBeInTheDocument();
    expect(screen.getByText("ED / Casualty")).toBeInTheDocument();
    expect(screen.getByText("Patient Queue")).toBeInTheDocument();
    expect(screen.getByText("Patient Encounters")).toBeInTheDocument();
    expect(screen.getByText("Bed Management")).toBeInTheDocument();
    expect(screen.getByText("Appointments")).toBeInTheDocument();
    expect(screen.getByText("Shift Handoff")).toBeInTheDocument();
    expect(screen.getByText("Control Tower")).toBeInTheDocument();

    expect(screen.getByRole("link", { name: /ED \/ Casualty/i })).toHaveAttribute(
      "href",
      "/clinical/emergency",
    );
    expect(screen.getByRole("link", { name: /Appointments/i })).toHaveAttribute(
      "href",
      "/scheduling",
    );
    expect(screen.getByRole("link", { name: /Bed Management/i })).toHaveAttribute(
      "href",
      "/beds",
    );
  });
});
