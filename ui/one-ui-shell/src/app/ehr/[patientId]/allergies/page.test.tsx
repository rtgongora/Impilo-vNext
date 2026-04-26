import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import AllergiesPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ patientId: "patient-1" }),
}));

vi.mock("@/components/EHRLayout", () => ({
  EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
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

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ user: { displayName: "Dr. Moyo", email: "moyo@example.com" } }),
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

vi.mock("@/hooks/queries/useAllergies", () => ({
  useAllergies: () => ({
    data: {
      data: [
        { id: "alg-1", attributes: { allergen: "Penicillin", allergenType: "MEDICATION", severity: "SEVERE", reaction: "Anaphylaxis", onsetDate: null, status: "ACTIVE" } },
      ],
    },
    isLoading: false,
  }),
  useCreateAllergy: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
}));

describe("AllergiesPage", () => {
  it("surfaces severe allergy safety context and medication continuity", () => {
    render(<AllergiesPage />);

    expect(screen.getByText("Surface severe reactions early and keep medication review linked to the same safety context")).toBeInTheDocument();
    expect(screen.getByText("Severe alerts")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Medications" })).toHaveAttribute("href", "/ehr/patient-1/medications");
  });
});
