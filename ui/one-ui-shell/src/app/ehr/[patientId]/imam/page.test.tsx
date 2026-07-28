import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

// The page composes three query hooks and renders ImamPanel. This test isolates the page's own
// logic — chiefly its handling of the encounters payload — so ImamPanel is stubbed.

vi.mock("next/navigation", () => ({ useParams: () => ({ patientId: "808fc9fd" }) }));
vi.mock("@/components/EHRLayout", () => ({ EHRLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));
vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (s: { facility: { id: string; name: string } }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Bangure Clinic" } }),
}));
vi.mock("@/features/paediatrics/imam/ImamPanel", () => ({
  ImamPanel: ({ encounterId }: { encounterId: string | null }) => (
    <div data-testid="imam-panel">encounter={String(encounterId)}</div>
  ),
}));

const { mockUsePatient, mockUseEncounters, mockUseGrowth } = vi.hoisted(() => ({
  mockUsePatient: vi.fn(),
  mockUseEncounters: vi.fn(),
  mockUseGrowth: vi.fn(),
}));
vi.mock("@/hooks/queries/usePatients", () => ({ usePatient: () => mockUsePatient() }));
vi.mock("@/hooks/queries/useEncounters", () => ({ useEncounters: () => mockUseEncounters() }));
vi.mock("@/hooks/queries/useGrowth", () => ({ useGrowth: () => mockUseGrowth() }));

import ImamPage from "./page";

describe("IMAM page — encounter payload handling", () => {
  it("does not crash when the encounters payload is journey-grouped rather than a flat resource list", () => {
    // The exact shape found live on the estate: /internal/v1/encounters returned
    // [{ journey, encounters }] — no top-level `attributes` — while the type declares
    // EncounterResource[]. The unguarded `encounter.attributes.status` took the whole page down
    // (ShellErrorBoundary → "Application error"). This asserts the page survives that shape.
    mockUsePatient.mockReturnValue({ data: { data: { attributes: { dateOfBirth: "2025-05-01" } } } });
    mockUseEncounters.mockReturnValue({ data: { data: [{ journey: { id: "j1" }, encounters: [] }] } });
    mockUseGrowth.mockReturnValue({ data: [] });

    expect(() => render(<ImamPage />)).not.toThrow();
    expect(screen.getByTestId("imam-panel")).toHaveTextContent("encounter=null");
  });

  it("still finds a genuinely active encounter when the flat shape is present", () => {
    mockUsePatient.mockReturnValue({ data: { data: { attributes: { dateOfBirth: "2025-05-01" } } } });
    mockUseEncounters.mockReturnValue({
      data: { data: [{ id: "enc-1", attributes: { status: "IN_PROGRESS" } }] },
    });
    mockUseGrowth.mockReturnValue({ data: [] });

    render(<ImamPage />);
    expect(screen.getByTestId("imam-panel")).toHaveTextContent("encounter=enc-1");
  });

  it("tolerates a malformed growth row without throwing", () => {
    mockUsePatient.mockReturnValue({ data: { data: { attributes: {} } } });
    mockUseEncounters.mockReturnValue({ data: { data: [] } });
    // a row missing measuredAt must not blow up the sort
    mockUseGrowth.mockReturnValue({ data: [{ muacCm: 11 }, { measuredAt: "2026-07-01", muacCm: 12 }] });

    expect(() => render(<ImamPage />)).not.toThrow();
  });
});
