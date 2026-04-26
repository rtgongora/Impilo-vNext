import type { ReactNode } from "react";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import OrdersPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ patientId: "patient-1" }),
}));

vi.mock("@/components/EHRLayout", () => ({
  EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({
    user: { id: "user-1", displayName: "Dr. Moyo", email: "moyo@example.com" },
  }),
}));

vi.mock("@/hooks/useRoleGroup", () => ({
  useRoleGroup: () => ({ isClinical: true }),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

vi.mock("@/hooks/queries/useEncounters", () => ({
  useEncounters: () => ({
    data: {
      data: [
        {
          id: "enc-1",
          attributes: {
            status: "IN_PROGRESS",
            encounterType: "OUTPATIENT",
            startedAt: "2026-04-08T09:00:00.000Z",
          },
        },
      ],
    },
  }),
}));

vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    post: vi.fn(),
  },
}));

vi.mock("@/hooks/queries/useImaging", () => ({
  useImagingStudies: () => ({ data: { data: [] }, isLoading: false, isError: false }),
}));

vi.mock("@/hooks/queries/useLabOrders", () => ({
  useLabOrders: () => ({
    data: {
      data: [
        {
          id: "order-1",
          attributes: {
            orderNumber: "LAB-001",
            testName: "Troponin",
            category: "LABORATORY",
            priority: "STAT",
            status: "ORDERED",
            orderedByName: "Dr. Moyo",
            createdAt: "2026-04-08T09:15:00.000Z",
          },
        },
        {
          id: "order-2",
          attributes: {
            orderNumber: "LAB-002",
            testName: "FBC",
            category: "LABORATORY",
            priority: "ROUTINE",
            status: "COLLECTED",
            orderedByName: "Dr. Moyo",
            createdAt: "2026-04-08T09:30:00.000Z",
          },
        },
        {
          id: "order-3",
          attributes: {
            orderNumber: "LAB-003",
            testName: "U&E",
            category: "LABORATORY",
            priority: "ROUTINE",
            status: "RESULTED",
            orderedByName: "Dr. Moyo",
            createdAt: "2026-04-08T10:00:00.000Z",
          },
        },
      ],
    },
    isLoading: false,
  }),
  useCreateLabOrder: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
  useCollectLabOrder: () => ({ mutate: vi.fn(), isPending: false }),
  useCancelLabOrder: () => ({ mutate: vi.fn(), isPending: false }),
}));

const { mockUseProductRegistrySearch } = vi.hoisted(() => ({
  mockUseProductRegistrySearch: vi.fn(),
}));

vi.mock("@/hooks/queries/useProductRegistry", () => ({
  useProductRegistrySearch: (params: unknown) => mockUseProductRegistrySearch(params),
}));

describe("OrdersPage", () => {
  it("surfaces diagnostic ordering orchestration with in-place workflow actions", () => {
    mockUseProductRegistrySearch.mockReturnValue({ data: { items: [] }, isLoading: false, isError: false });
    render(<OrdersPage />);

    expect(screen.getByText("Order, collect, result, and review diagnostics from the same encounter-aware workspace")).toBeInTheDocument();
    expect(screen.getByText("Pending collection")).toBeInTheDocument();
    expect(screen.getByText("Ready for result entry")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add Order" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Collect" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Enter Result" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Results" })).toHaveAttribute("href", "/ehr/patient-1/results");
  });

  it("allows product-registry search to fill test name/code (no fake catalog)", () => {
    mockUseProductRegistrySearch.mockImplementation((params: any) => {
      if (params?.q === "cbc") {
        return {
          data: { items: [{ id: "svc-1", name: "Complete Blood Count", code: "CBC", kind: "service" }] },
          isLoading: false,
          isError: false,
        };
      }
      return { data: { items: [] }, isLoading: false, isError: false };
    });

    render(<OrdersPage />);

    fireEvent.click(screen.getByRole("button", { name: "Add Order" }));
    fireEvent.change(screen.getByLabelText("Search term"), { target: { value: "cbc" } });

    expect(screen.getByText(/1 match/)).toBeInTheDocument();
    const lookupCard = screen.getByText("Product / service lookup (shared rail)").closest("div");
    expect(lookupCard).toBeTruthy();
    const table = within(lookupCard as HTMLElement).getByRole("table");
    expect(within(table).getByText("Complete Blood Count")).toBeInTheDocument();
    fireEvent.click(within(table).getByRole("button", { name: "Use" }));

    expect(screen.getByDisplayValue("Complete Blood Count")).toBeInTheDocument();
    expect(screen.getByDisplayValue("CBC")).toBeInTheDocument();
  });
});
