import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { FacilityWorkClusterRibbon } from "./FacilityWorkClusterRibbon";

const mockSearch = vi.fn((_key: string): string | null => null);

vi.mock("next/navigation", () => ({
  useSearchParams: () => ({ get: mockSearch }),
}));

const setSub = vi.fn();
const setMode = vi.fn();

vi.mock("@/hooks/useOperationalContextStore", () => ({
  useOperationalContextStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      facilityWorkSubcontext: null,
      setFacilityWorkSubcontext: setSub,
      setOperationalMode: setMode,
    }),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (s: { facility: { name: string } | null }) => unknown) =>
    selector({ facility: { name: "Test Facility" } }),
}));

vi.mock("@/hooks/useWorkspaceStore", () => ({
  useWorkspaceStore: (selector: (s: { workspace: { name: string } | null }) => unknown) =>
    selector({ workspace: { name: "ER Pod A" } }),
}));

vi.mock("@/hooks/useShiftStore", () => ({
  useShiftStore: (selector: (s: { shift: { id: string } | null }) => unknown) =>
    selector({ shift: { id: "shift-1" } }),
}));

describe("FacilityWorkClusterRibbon", () => {
  beforeEach(() => {
    mockSearch.mockReset();
    setSub.mockReset();
    setMode.mockReset();
  });

  it("hides on organization-admin plane entry", () => {
    mockSearch.mockImplementation((k) => (k === "from" ? "organization-admin" : null));
    const { container } = render(<FacilityWorkClusterRibbon shiftExpected />);
    expect(container.firstChild).toBeNull();
    expect(setMode).not.toHaveBeenCalled();
  });

  it("sets facility_work mode and surfaces facility context", () => {
    mockSearch.mockImplementation(() => null);
    render(<FacilityWorkClusterRibbon shiftExpected activeEncounterCount={2} />);
    expect(setMode).toHaveBeenCalledWith("facility_work");
    expect(screen.getByText(/Test Facility/)).toBeInTheDocument();
    expect(screen.getByText(/2/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Open Supervisor/i })).toHaveAttribute("href", "/queue");
  });

  it("hydrates subcontext from query string", () => {
    mockSearch.mockImplementation((k) => (k === "subcontext" ? "pharmacy" : null));
    render(<FacilityWorkClusterRibbon shiftExpected={false} />);
    expect(setSub).toHaveBeenCalledWith("pharmacy");
  });

  it("selecting a focus updates the store", () => {
    mockSearch.mockImplementation(() => null);
    render(<FacilityWorkClusterRibbon shiftExpected={false} />);
    fireEvent.click(screen.getByRole("button", { name: "Triage" }));
    expect(setSub).toHaveBeenCalledWith("triage");
  });
});
