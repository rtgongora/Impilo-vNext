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
  useQuery: (opts: { queryKey: unknown[] }) => {
    const key = opts.queryKey;
    if (key[0] === "finance" && key[1] === "costa-intel") {
      return {
        data: [
          {
            id: 1,
            externalCode: "ZW-PLACEHOLDER-POC-V0.1",
            name: "Zimbabwe Placeholder Health Services Tariff — Proof of Concept v0.1",
            description: "Placeholder proof-of-concept tariff for demos",
            tariffFamily: "zimbabwe_placeholder",
            tariffType: "operational",
            priceBasis: "placeholder_rate",
            officialStatus: "placeholder",
            validationStatus: "validated",
            referenceOnly: false,
            approvedForBilling: true,
            currency: "USD",
            effectiveFrom: "2020-01-01",
            effectiveTo: null,
            metadata: { default_for_demo: true },
          },
        ],
        isLoading: false,
        error: null,
      };
    }
    return {
      data: { data: [] },
      isLoading: false,
      error: null,
    };
  },
}));

describe("TariffsPage", () => {
  it("loads COSTA tariff library lists and keeps encounter-aware finance continuity", () => {
    render(<TariffsPage />);

    expect(screen.getByText("Tariff library")).toBeInTheDocument();
    expect(screen.getByText("Tariff continuity")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Encounter" })).toHaveAttribute("href", "/ehr/patient-1/encounter/enc-1");
    expect(screen.getByRole("link", { name: "Billing" })).toHaveAttribute(
      "href",
      "/finance/billing?patientId=patient-1&encounterId=enc-1&source=discharge",
    );
    expect(screen.getAllByText(/Zimbabwe Placeholder Health Services Tariff/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/Reference tariffs/)).toBeInTheDocument();
  });
});
