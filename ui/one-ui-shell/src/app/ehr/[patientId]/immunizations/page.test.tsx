import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ImmunizationsPage from "./page";

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
    user: { displayName: "Nurse Moyo", email: "nurse@example.com" },
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
          },
        },
      ],
    },
  }),
}));

vi.mock("@/hooks/queries/useImmunizations", () => ({
  useImmunizations: () => ({
    data: {
      data: [
        {
          id: "imm-1",
          attributes: {
            vaccineName: "Influenza",
            vaccineCode: "CVX-141",
            doseNumber: 1,
            doseSequence: "1 of 1",
            lotNumber: "AB1234",
            site: "Left deltoid",
            route: "IM",
            status: "COMPLETED",
            administeredBy: "Nurse Moyo",
            administeredAt: "2026-04-08T09:30:00.000Z",
            notes: "Tolerated well",
            createdAt: "2026-04-08T09:30:00.000Z",
          },
        },
      ],
    },
    isLoading: false,
  }),
  useRecordImmunization: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
}));

describe("ImmunizationsPage", () => {
  it("surfaces immunization review continuity with history and notes", () => {
    render(<ImmunizationsPage />);

    expect(screen.getByText("Confirm vaccine history inside the same review flow before the encounter moves on")).toBeInTheDocument();
    expect(screen.getByText("Immunization continuity")).toBeInTheDocument();
    expect(screen.getByText("Documented doses")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "History" })).toHaveAttribute("href", "/ehr/patient-1/history");
    expect(screen.getByRole("link", { name: "Notes" })).toHaveAttribute("href", "/ehr/patient-1/notes");
  });
});
