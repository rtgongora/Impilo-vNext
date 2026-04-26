import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import PharmacyStockPage from "./page";

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
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

vi.mock("@tanstack/react-query", () => ({
  useQuery: () => ({
    data: {
      data: [
        {
          id: "stock-1",
          type: "pharmacy_stock",
          attributes: {
            medicationName: "Salbutamol inhaler",
            currentStock: 2,
            reorderLevel: 5,
            unit: "each",
            lastRestocked: "2026-04-01T09:00:00.000Z",
          },
        },
      ],
    },
    isLoading: false,
    error: null,
  }),
}));

describe("PharmacyStockPage", () => {
  it("surfaces stock risk as part of the medication closure loop", () => {
    render(<PharmacyStockPage />);

    expect(screen.getByText("Stock continuity")).toBeInTheDocument();
    expect(screen.getByText("Stock loop status")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Encounter" })).toHaveAttribute("href", "/ehr/patient-1/encounter/enc-1");
    expect(screen.getByText("Low Stock")).toBeInTheDocument();
    expect(screen.getByText("Salbutamol inhaler")).toBeInTheDocument();
  });
});
