import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import MedicationsPage from "./page";

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

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

vi.mock("@/hooks/useRoleGroup", () => ({
  useRoleGroup: () => ({ isPrescriber: true, isDispenser: true }),
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
  useQuery: () => ({
    data: {
      data: [
        { id: "rx-1", attributes: { medication_name: "Amoxicillin", status: "PENDING", dosage: "500mg", route: "oral", frequency: "BD" } },
        { id: "rx-2", attributes: { medication_name: "Amlodipine", status: "DISPENSED", dosage: "5mg", route: "oral", frequency: "OD" } },
      ],
    },
    isLoading: false,
  }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    post: vi.fn(),
  },
}));

describe("MedicationsPage", () => {
  it("surfaces medication review orchestration with orders and results continuity", () => {
    render(<MedicationsPage />);

    expect(screen.getByText("Prescribe, dispense, and reconcile treatment while keeping the encounter and safety context visible")).toBeInTheDocument();
    expect(screen.getByText("Pending dispense")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Orders" })).toHaveAttribute("href", "/ehr/patient-1/orders");
    expect(screen.getByRole("link", { name: "Results" })).toHaveAttribute("href", "/ehr/patient-1/results");
  });
});
