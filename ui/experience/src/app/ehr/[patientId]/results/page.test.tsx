import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ResultsPage from "./page";

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
            status: "RESULTED",
            orderedByName: "Dr. Moyo",
            resultedAt: "2026-04-08T11:00:00.000Z",
            collectedAt: "2026-04-08T10:00:00.000Z",
            result_data: [
              {
                name: "Troponin I",
                value: "0.8",
                unit: "ng/mL",
                referenceRange: "0-0.04",
                interpretation: "CRITICAL",
              },
            ],
          },
        },
        {
          id: "order-2",
          attributes: {
            orderNumber: "LAB-002",
            testName: "FBC",
            category: "LABORATORY",
            status: "REVIEWED",
            orderedByName: "Dr. Moyo",
            resultedAt: "2026-04-07T08:00:00.000Z",
            collectedAt: "2026-04-07T07:00:00.000Z",
            result_data: [
              {
                name: "Hemoglobin",
                value: "13",
                unit: "g/dL",
                referenceRange: "12-16",
                interpretation: "NORMAL",
              },
            ],
          },
        },
      ],
    },
    isLoading: false,
  }),
}));

describe("ResultsPage", () => {
  it("surfaces result review orchestration and in-place acknowledgement", () => {
    render(<ResultsPage />);

    expect(screen.getByText("Review incoming results, acknowledge what is ready, and carry the next decision back into the encounter")).toBeInTheDocument();
    expect(screen.getByText("Awaiting review")).toBeInTheDocument();
    expect(screen.getByText("Critical flags")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Acknowledge result" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Orders" })).toHaveAttribute("href", "/ehr/patient-1/orders");
  });
});
