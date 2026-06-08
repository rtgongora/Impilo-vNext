import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import FinancePage from "./page";

vi.mock("next/navigation", () => ({
  useSearchParams: () => ({
    get: (key: string) =>
      ({
        patientId: "patient-1",
        encounterId: "enc-1",
        source: "discharge",
      })[key] ?? null,
  }),
}));

vi.mock("@/components/experience/OrganizationPlaneContextBar", () => ({
  OrganizationPlaneContextBar: () => null,
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title, subtitle }: { children: ReactNode; title: string; subtitle?: string }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

vi.mock("@/hooks/queries/useServiceAccessDecisions", () => ({
  useServiceAccessDecisionsList: () => ({ data: [], isLoading: false, isError: false }),
}));

describe("FinancePage", () => {
  it("keeps finance entry anchored to the linked encounter and downstream revenue surfaces", () => {
    render(<FinancePage />);

    expect(screen.getByText("Revenue follow-through")).toBeInTheDocument();
    expect(screen.getByText("Revenue loop status")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Encounter" })).toHaveAttribute("href", "/ehr/patient-1/encounter/enc-1");
    expect(screen.getByRole("link", { name: "Billing" })).toHaveAttribute(
      "href",
      "/finance/billing?patientId=patient-1&encounterId=enc-1&source=discharge",
    );
    expect(screen.getByRole("link", { name: /Open integration map/i })).toHaveAttribute(
      "href",
      "/finance/commerce-integrations",
    );
    expect(screen.getByRole("link", { name: /MSIKA Governance/i })).toHaveAttribute(
      "href",
      "/finance/msika-governance?patientId=patient-1&encounterId=enc-1&source=discharge",
    );
  });
});
