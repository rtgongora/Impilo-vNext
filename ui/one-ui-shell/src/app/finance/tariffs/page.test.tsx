import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import TariffsPage from "./page";

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
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } | null }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

vi.mock("@tanstack/react-query", () => ({
  useQuery: () => ({
    data: {
      data: [
        {
          id: "tariff-1",
          type: "tariff",
          attributes: {
            serviceCode: "CONS-001",
            description: "Consultation",
            tariffAmount: 25,
            currency: "USD",
            effectiveDate: "2026-04-01T00:00:00.000Z",
            status: "ACTIVE",
          },
        },
        {
          id: "tariff-2",
          type: "tariff",
          attributes: {
            serviceCode: "XR-001",
            description: "Chest X-ray",
            tariffAmount: 45,
            currency: "USD",
            effectiveDate: "2026-04-01T00:00:00.000Z",
            status: "DRAFT",
          },
        },
      ],
    },
    isLoading: false,
    error: null,
  }),
}));

describe("TariffsPage", () => {
  it("keeps tariff review connected to encounter-aware finance continuity", () => {
    render(<TariffsPage />);

    expect(screen.getByText("Tariff continuity")).toBeInTheDocument();
    expect(screen.getByText("Tariff loop status")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Encounter" })).toHaveAttribute("href", "/ehr/patient-1/encounter/enc-1");
    expect(screen.getByRole("link", { name: "Billing" })).toHaveAttribute(
      "href",
      "/finance/billing?patientId=patient-1&encounterId=enc-1&source=discharge",
    );
    expect(screen.getByText("CONS-001")).toBeInTheDocument();
  });
});
