import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ClinicalHistoryPage from "./page";

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
            chief_complaint: "Persistent chest pain",
          },
        },
      ],
    },
    isLoading: false,
  }),
}));

vi.mock("@tanstack/react-query", () => ({
  useQuery: ({ queryKey }: { queryKey: [string] }) => {
    const [resource] = queryKey;
    if (resource === "conditions") {
      return {
        data: {
          data: [
            { id: "cond-1", attributes: { clinicalStatus: "ACTIVE", conditionName: "Hypertension", severity: "MODERATE" } },
          ],
        },
        isLoading: false,
      };
    }
    if (resource === "allergies") {
      return {
        data: {
          data: [
            { id: "alg-1", attributes: { status: "ACTIVE", allergen: "Penicillin", severity: "SEVERE" } },
          ],
        },
        isLoading: false,
      };
    }
    if (resource === "prescriptions") {
      return {
        data: {
          data: [
            { id: "rx-1", attributes: { status: "ACTIVE", medicationName: "Amlodipine", dosage: "5mg", frequency: "OD" } },
          ],
        },
        isLoading: false,
      };
    }
    return {
      data: { data: [] },
      isLoading: false,
    };
  },
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    post: vi.fn(),
    get: vi.fn(),
  },
}));

describe("ClinicalHistoryPage", () => {
  it("surfaces longitudinal review orchestration above the history workspace", () => {
    render(<ClinicalHistoryPage />);

    expect(screen.getByText("Review the active story, then branch into conditions, medications, and allergies without losing encounter context")).toBeInTheDocument();
    expect(screen.getByText("Active problems")).toBeInTheDocument();
    expect(screen.getByText("Medication review")).toBeInTheDocument();
    expect(screen.getByText("Allergy alerts")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Conditions" })).toHaveAttribute("href", "/ehr/patient-1/conditions");
  });
});
