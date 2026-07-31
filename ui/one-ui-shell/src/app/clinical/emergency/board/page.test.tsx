import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import EmergencyEpisodeBoardPage from "./page";

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } | null }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Test Hospital" } }),
}));

const mockBoard = vi.fn();
const mockOpenEpisode = vi.fn(() => ({ mutate: vi.fn(), isPending: false }));

vi.mock("@/hooks/queries/useEmergencyEpisode", () => ({
  useEmergencyEpisodeBoard: () => mockBoard(),
  useOpenEmergencyEpisode: () => mockOpenEpisode(),
}));

describe("EmergencyEpisodeBoardPage", () => {
  it("shows an empty state when there are no open episodes", () => {
    mockBoard.mockReturnValue({ data: [], isLoading: false, isError: false });
    render(<EmergencyEpisodeBoardPage />);
    expect(screen.getByText(/No open episodes/i)).toBeInTheDocument();
  });

  it("renders each open episode as a link to its spine detail page", () => {
    mockBoard.mockReturnValue({
      data: [
        {
          episode_id: "ep-1",
          episode_reference: "EM-2607-ABCDE",
          state: "OPEN_IN_CARE",
          entry_route: "WALK_IN",
          subject_cpid: "cpid-1",
          current_location: "EMERGENCY_UNIT",
        },
      ],
      isLoading: false,
      isError: false,
    });
    render(<EmergencyEpisodeBoardPage />);
    const link = screen.getByText("EM-2607-ABCDE").closest("a");
    expect(link).toHaveAttribute("href", "/clinical/emergency/spine/ep-1");
    expect(screen.getByText("In care")).toBeInTheDocument();
  });

  it("shows a loading state", () => {
    mockBoard.mockReturnValue({ data: undefined, isLoading: true, isError: false });
    render(<EmergencyEpisodeBoardPage />);
    expect(screen.getByText(/Loading board/i)).toBeInTheDocument();
  });

  it("shows an error state distinctly from empty", () => {
    mockBoard.mockReturnValue({ data: undefined, isLoading: false, isError: true });
    render(<EmergencyEpisodeBoardPage />);
    expect(screen.getByText(/Could not load the emergency board/i)).toBeInTheDocument();
  });
});
