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

vi.mock("@/components/intelligent/NompiloContextPanel", () => ({
  NompiloContextPanel: () => null,
}));

describe("ClinicalHubPage", () => {
  it("renders the clinical care hub with all module tiles", () => {
    render(<ClinicalHubPage />);

    expect(screen.getByRole("heading", { level: 1, name: "Clinical Care" })).toBeInTheDocument();
    expect(
      screen.getByText("Outpatient, inpatient, emergency, orders, Rx, and clinical operations"),
    ).toBeInTheDocument();
  });

  it("shows Wave 20 clinical module tiles with correct links", () => {
    render(<ClinicalHubPage />);

    expect(screen.getByText("My Dashboard")).toBeInTheDocument();
    expect(screen.getByText("ED / Casualty")).toBeInTheDocument();
    expect(screen.getByText("Inpatient workspace")).toBeInTheDocument();
    expect(screen.getByText("Nursing workbench")).toBeInTheDocument();
    expect(screen.getByText("Rx / pharmacy")).toBeInTheDocument();
    expect(screen.getByText("Control tower")).toBeInTheDocument();

    expect(screen.getByRole("link", { name: /ED \/ Casualty/i })).toHaveAttribute(
      "href",
      "/clinical/emergency",
    );
    expect(screen.getByRole("link", { name: /Inpatient workspace/i })).toHaveAttribute(
      "href",
      "/clinical/inpatient",
    );
    expect(screen.getByRole("link", { name: /Nursing workbench/i })).toHaveAttribute(
      "href",
      "/clinical/inpatient/nursing",
    );
    expect(screen.getByRole("link", { name: /Rx \/ pharmacy/i })).toHaveAttribute(
      "href",
      "/pharmacy",
    );
  });
});
