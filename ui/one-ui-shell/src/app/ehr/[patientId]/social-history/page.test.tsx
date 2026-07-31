import type { ReactNode } from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import SocialHistoryPage from "./page";

vi.mock("next/navigation", () => ({ useParams: () => ({ patientId: "patient-1" }) }));
vi.mock("@/components/EHRLayout", () => ({ EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({ PageShell: ({ children, title }: { children: ReactNode; title: string }) => <div><h1>{title}</h1>{children}</div> }));
vi.mock("@/hooks/useFacilityStore", () => ({ useFacilityStore: (selector: (state: { facility: { id: string; name: string } }) => unknown) => selector({ facility: { id: "facility-1", name: "Harare Central" } }) }));
vi.mock("@/hooks/queries/useEncounters", () => ({ useEncounters: () => ({ data: { data: [{ id: "enc-1", attributes: { status: "IN_PROGRESS", encounterType: "OUTPATIENT", startedAt: "2026-04-08T09:00:00.000Z" } }] } }) }));

const { recordSocialHistoryMutate } = vi.hoisted(() => ({ recordSocialHistoryMutate: vi.fn() }));

vi.mock("@/hooks/queries/useStructuredHistory", () => ({
  __esModule: true,
  useSocialHistory: () => ({
    data: {
      data: [
        {
          id: "s-1",
          category: "Smoking",
          icon: "home",
          status: "Former Smoker",
          detail: "Quit 3 years ago",
          riskLevel: "Moderate",
          lastUpdated: "2026-03-15",
        },
      ],
    },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useRecordSocialHistory: () => ({
    mutate: recordSocialHistoryMutate,
    reset: vi.fn(),
    isPending: false,
    isError: false,
  }),
}));

describe("SocialHistoryPage", () => {
  it("surfaces social continuity into goals and care plans", () => {
    render(<SocialHistoryPage />);

    expect(screen.getByText("Keep practical barriers and supports visible so plans and goals still match real life outside the visit")).toBeInTheDocument();
    expect(screen.getByText("Social continuity")).toBeInTheDocument();
    expect(screen.getByText("Moderate risk")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Care Plans" })).toHaveAttribute("href", "/ehr/patient-1/care-plans");
    expect(screen.getByRole("link", { name: "Goals" })).toHaveAttribute("href", "/ehr/patient-1/goals");
  });

  it("wires the Save button to the record mutation with the edited fields", () => {
    render(<SocialHistoryPage />);

    fireEvent.click(screen.getByRole("button", { name: "Edit Smoking" }));
    fireEvent.change(screen.getByLabelText("Status"), { target: { value: "Current Smoker" } });
    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    expect(recordSocialHistoryMutate).toHaveBeenCalledWith(
      expect.objectContaining({ category: "Smoking", status: "Current Smoker", riskLevel: "MODERATE" }),
      expect.anything()
    );
  });
});
