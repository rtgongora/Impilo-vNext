import type { ReactNode } from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import BedManagementPage from "./page";

const mutate = vi.fn();

vi.mock("next/navigation", () => ({
  useSearchParams: () => ({
    get: (key: string) => {
      if (key === "patientId") return "patient-1";
      if (key === "encounterId") return "enc-1";
      if (key === "source") return "discharge";
      if (key === "disposition") return "ADMIT";
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
    if (queryKey[0] === "wards") {
      return {
        data: {
          data: [
            {
              id: "ward-1",
              type: "ward",
              attributes: {
                name: "Medical Ward",
                wardType: "GENERAL",
                floor: "1",
                totalBeds: 10,
                availableBeds: 4,
                occupiedBeds: 5,
                status: "ACTIVE",
              },
            },
          ],
        },
        isLoading: false,
      };
    }

    return {
      data: {
        data: [
          {
            id: "bed-1",
            type: "bed",
            attributes: {
              bedNumber: "4B",
              bedType: "GENERAL",
              status: "AVAILABLE",
              wardId: "ward-1",
              wardName: "Medical Ward",
              acuity: null,
              patientId: null,
              patientName: null,
              admittedAt: null,
              assignedDoctor: null,
            },
          },
          {
            id: "bed-2",
            type: "bed",
            attributes: {
              bedNumber: "5A",
              bedType: "GENERAL",
              status: "OCCUPIED",
              wardId: "ward-1",
              wardName: "Medical Ward",
              acuity: "HIGH",
              patientId: "patient-9",
              patientName: "M. Ncube",
              admittedAt: null,
              assignedDoctor: null,
            },
          },
        ],
      },
      isLoading: false,
    };
  },
  useMutation: () => ({ mutate }),
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}));

vi.mock("@/lib/api-client", () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }));

describe("BedManagementPage", () => {
  it("keeps admission handoff context visible while reserving beds in place", () => {
    render(<BedManagementPage />);

    expect(screen.getByText("Admission continuity")).toBeInTheDocument();
    expect(screen.getByText("Bed handoff status")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Encounter" })).toHaveAttribute(
      "href",
      "/ehr/patient-1/encounter/enc-1",
    );

    fireEvent.click(screen.getByRole("button", { name: "Reserve" }));

    expect(mutate).toHaveBeenCalledWith({ bedId: "bed-1", status: "RESERVED" });
  });
});
