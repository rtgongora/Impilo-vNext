import type { ReactNode } from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import VisitOutcomePage from "./page";

const pushMock = vi.fn();
const postMock = vi.fn().mockResolvedValue({ data: { attributes: { costa_bill_id: "bill-1" } } });

vi.mock("next/navigation", () => ({
  useParams: () => ({ patientId: "patient-1" }),
  useRouter: () => ({ push: pushMock }),
  useSearchParams: () => ({ get: (key: string) => (key === "encounterId" ? "enc-2" : null) }),
}));
vi.mock("@/components/EHRLayout", () => ({ EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({ PageShell: ({ children, title }: { children: ReactNode; title: string }) => <div><h1>{title}</h1>{children}</div> }));
vi.mock("@/hooks/useFacilityStore", () => ({ useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) => selector({ facility: { id: "facility-1", name: "Harare Central Hospital" } }) }));
vi.mock("@/hooks/useAuthStore", () => ({ useAuthStore: () => ({ user: { id: "user-1" } }) }));
vi.mock("@/hooks/useRoleGroup", () => ({ useRoleGroup: () => ({ isClinical: true }) }));
vi.mock("@/hooks/queries/usePatients", () => ({ usePatient: () => ({ data: { data: { id: "patient-1", attributes: { fullName: "Tariro Moyo" } } } }) }));
vi.mock("@/hooks/queries/useEncounters", () => ({
  useEncounters: () => ({
    data: {
      data: [
        { id: "enc-1", attributes: { status: "IN_PROGRESS", encounterType: "OUTPATIENT", startedAt: "2026-04-08T09:00:00.000Z" } },
        { id: "enc-2", attributes: { status: "ACTIVE", encounterType: "EMERGENCY", startedAt: "2026-04-08T10:00:00.000Z" } },
      ],
    },
    isLoading: false,
  }),
}));
vi.mock("@/lib/api-client", () => ({ apiClient: { post: (...args: unknown[]) => postMock(...args) } }));

describe("VisitOutcomePage", () => {
  it("anchors outcome completion to the encounter in scope and keeps closure continuity visible", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <VisitOutcomePage />
      </QueryClientProvider>
    );

    expect(screen.getByText("Outcome closure")).toBeInTheDocument();
    expect(screen.getByText("Outcome loop status")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Encounter" })).toHaveAttribute("href", "/ehr/patient-1/encounter/enc-2");

    fireEvent.click(screen.getByRole("button", { name: /Discharge/ }));
    fireEvent.click(screen.getByRole("button", { name: /Complete Encounter/ }));

    await waitFor(() => {
      expect(postMock).toHaveBeenCalledWith(
        "/internal/v1/encounters/enc-2/discharge",
        expect.objectContaining({ discharge_type: "CLINICAL", discharged_by: "user-1" })
      );
    });
  });
});
