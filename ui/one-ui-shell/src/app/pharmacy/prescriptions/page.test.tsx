import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import PrescriptionsPage from "./page";

vi.mock("next/navigation", () => ({
  useSearchParams: () => ({
    get: (key: string) =>
      ({
        patientId: "patient-1",
        encounterId: "enc-1",
        source: "discharge",
      })[key] ?? null,
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
    selector({ facility: { id: "facility-1", name: "Harare Central" } }),
}));

vi.mock("@/hooks/queries/usePharmacy", () => ({
  usePrescriptions: () => ({
    data: {
      data: [
        {
          id: "rx-1",
          attributes: {
            patientId: "patient-1",
            status: "PENDING",
            patientName: "Tariro Moyo",
            prescriberName: "Dr. Moyo",
            createdAt: "2026-04-08T09:00:00.000Z",
            items: [{ medication: "Amoxicillin", dosage: "500 mg" }],
          },
        },
      ],
    },
    isLoading: false,
    error: null,
  }),
}));

describe("PrescriptionsPage", () => {
  it("surfaces prescription continuity with direct links into dispense and chart context", () => {
    render(<PrescriptionsPage />);

    expect(screen.getByText("Prescription continuity")).toBeInTheDocument();
    expect(screen.getByText("Prescription loop status")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Encounter" })).toHaveAttribute("href", "/ehr/patient-1/encounter/enc-1");
    expect(screen.getByRole("link", { name: "Dispense" })).toHaveAttribute(
      "href",
      "/pharmacy/dispense?patientId=patient-1&encounterId=enc-1&source=discharge",
    );
    expect(screen.getByText("Amoxicillin")).toBeInTheDocument();
  });
});
