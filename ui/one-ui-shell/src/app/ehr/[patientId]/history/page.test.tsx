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
            isOpen: true,
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

vi.mock("@/features/medicine/clerking/ClerkingContinuityShell", () => ({
  ClerkingContinuityShell: ({ patientId }: { patientId: string }) => (
    <div data-testid="clerking-continuity-shell">Clerking for {patientId}</div>
  ),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    post: vi.fn(),
    get: vi.fn(),
  },
}));

describe("ClinicalHistoryPage", () => {
  it("keeps HPI narrative and mounts clerking continuity compose", () => {
    render(<ClinicalHistoryPage />);

    expect(
      screen.getByText(
        /Review the active story, then branch into structured continuity/
      )
    ).toBeInTheDocument();
    expect(screen.getByText("Presenting Complaint")).toBeInTheDocument();
    expect(screen.getByText("Persistent chest pain")).toBeInTheDocument();
    expect(screen.getByText("History of Present Illness")).toBeInTheDocument();
    expect(screen.getByTestId("clerking-continuity-shell")).toHaveTextContent("Clerking for patient-1");
    expect(screen.getByRole("link", { name: "Conditions" })).toHaveAttribute(
      "href",
      "/ehr/patient-1/conditions"
    );
  });
});
