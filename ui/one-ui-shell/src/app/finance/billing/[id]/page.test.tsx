import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import BillingDetailPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ id: "bill-1" }),
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

vi.mock("@tanstack/react-query", () => ({
  useQuery: ({ queryKey }: { queryKey: unknown[] }) => {
    if (queryKey[0] === "finance-billing" && queryKey[1] === "bill-1") {
      return {
        data: {
          data: {
            id: "bill-1",
            type: "invoice",
            attributes: {
              invoiceNumber: "INV-001",
              patient: "Tariro Moyo",
              amount: 120,
              currency: "USD",
              status: "DRAFT",
              date: "2026-04-08T00:00:00.000Z",
              lineItems: [{ description: "Consultation", amount: 120 }],
            },
          },
        },
        isLoading: false,
        error: null,
      };
    }

    return {
      data: { data: [] },
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    };
  },
  useMutation: () => ({
    mutate: vi.fn(),
    isPending: false,
    isSuccess: false,
    isError: false,
  }),
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}));

vi.mock("@/lib/api-client", () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }));

describe("BillingDetailPage", () => {
  it("keeps bill actions connected to the source encounter and chart context", () => {
    render(<BillingDetailPage />);

    expect(screen.getByText("Finance closure")).toBeInTheDocument();
    expect(screen.getByText("Bill loop status")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Encounter" })).toHaveAttribute(
      "href",
      "/ehr/patient-1/encounter/enc-1",
    );
    expect(screen.getByText("Submit for approval")).toBeInTheDocument();
  });
});
