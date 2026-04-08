import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ClaimDetailPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ id: "claim-1" }),
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
      data: {
        id: "claim-1",
        attributes: {
          claimNumber: "CLM-100",
          patient: "Tariro Moyo",
          scheme: "Cimas",
          amount: 90,
          currency: "USD",
          status: "SUBMITTED",
          date: "2026-04-08T09:00:00.000Z",
          lineItems: [
            {
              code: "XR001",
              description: "Chest X-ray",
              quantity: 1,
              unitPrice: 90,
              total: 90,
            },
          ],
          adjudicationHistory: [],
        },
      },
    },
    isLoading: false,
    error: null,
  }),
}));

describe("ClaimDetailPage", () => {
  it("keeps claim adjudication connected to encounter and finance continuity", () => {
    render(<ClaimDetailPage />);

    expect(screen.getByText("Claim closure")).toBeInTheDocument();
    expect(screen.getByText("Claim loop status")).toBeInTheDocument();
    expect(screen.getByText("Await adjudication")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Encounter" })).toHaveAttribute("href", "/ehr/patient-1/encounter/enc-1");
    expect(screen.getByRole("link", { name: "Billing" })).toHaveAttribute(
      "href",
      "/finance/billing?patientId=patient-1&encounterId=enc-1&source=discharge",
    );
  });
});
