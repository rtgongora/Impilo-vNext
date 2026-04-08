import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ClaimsPage from "./page";

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
          id: "claim-1",
          attributes: {
            claimNumber: "CLM-100",
            patient: "Tariro Moyo",
            scheme: "Cimas",
            amount: 90,
            currency: "USD",
            status: "SUBMITTED",
            date: "2026-04-08T09:00:00.000Z",
          },
        },
      ],
    },
    isLoading: false,
    error: null,
  }),
}));

describe("ClaimsPage", () => {
  it("keeps claim list continuity tied to the encounter and preserved claim links", () => {
    render(<ClaimsPage />);

    expect(screen.getByText("Claims continuity")).toBeInTheDocument();
    expect(screen.getByText("Claim loop status")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Encounter" })).toHaveAttribute("href", "/ehr/patient-1/encounter/enc-1");
    expect(screen.getByRole("link", { name: "CLM-100" })).toHaveAttribute(
      "href",
      "/finance/claims/claim-1?patientId=patient-1&encounterId=enc-1&source=discharge",
    );
  });
});
