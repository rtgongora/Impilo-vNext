import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import BillingPage from "./page";

vi.mock("next/navigation", () => ({
  useSearchParams: () => ({
    get: (key: string) => {
      if (key === "patientId") return "patient-1";
      if (key === "encounterId") return "enc-1";
      if (key === "source") return "discharge";
      return null;
    },
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
    selector({ facility: { id: "facility-1", name: "Harare Central Hospital" } }),
}));

vi.mock("@tanstack/react-query", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@tanstack/react-query")>();
  return {
    ...actual,
    useQuery: () => ({
      data: {
        data: [
          {
            id: "bill-1",
            type: "invoice",
            attributes: {
              invoiceNumber: "INV-001",
              patient: "Tariro Moyo",
              amount: 120,
              currency: "USD",
              status: "DRAFT",
              date: "2026-04-08T00:00:00.000Z",
            },
          },
          {
            id: "bill-2",
            type: "invoice",
            attributes: {
              invoiceNumber: "INV-002",
              patient: "Simba Ncube",
              amount: 450,
              currency: "USD",
              status: "FINAL",
              date: "2026-04-07T00:00:00.000Z",
            },
          },
        ],
      },
      isLoading: false,
      error: null,
    }),
    useQueryClient: () => ({
      invalidateQueries: vi.fn(),
    }),
    useMutation: () => ({
      mutate: vi.fn(),
      mutateAsync: vi.fn(),
      isPending: false,
      isSuccess: false,
      isError: false,
      error: null,
    }),
  };
});

vi.mock("@/lib/api-client", () => ({ apiClient: { get: vi.fn() } }));

describe("BillingPage", () => {
  it("keeps encounter-linked billing continuity visible from the invoice list", () => {
    render(<BillingPage />);

    expect(screen.getByText("Billing follow-through")).toBeInTheDocument();
    expect(screen.getByText("Billing loop status")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Encounter" })).toHaveAttribute(
      "href",
      "/ehr/patient-1/encounter/enc-1",
    );
    expect(screen.getByRole("link", { name: "INV-001" })).toHaveAttribute(
      "href",
      "/finance/billing/bill-1?patientId=patient-1&encounterId=enc-1&source=discharge",
    );
  });
});
