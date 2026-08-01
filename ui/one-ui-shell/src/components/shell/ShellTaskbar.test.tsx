import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ShellTaskbar } from "./ShellTaskbar";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/home",
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (sel?: (s: { hasRole: (r: string) => boolean }) => unknown) => {
    const s = { hasRole: () => true };
    return sel ? sel(s) : s;
  },
}));

vi.mock("@/hooks/useShellStore", () => ({
  useShellStore: (sel: (s: Record<string, unknown>) => unknown) => {
    const state = {
      toggleStart: vi.fn(),
      toggleSearch: vi.fn(),
      setTaskManagerOpen: vi.fn(),
      pinnedAppCodes: [] as string[],
      openTasks: [] as unknown[],
      activeTaskId: null,
      setActiveTask: vi.fn(),
      minimizeTask: vi.fn(),
      closeTask: vi.fn(),
      togglePinApp: vi.fn(),
      sosDialogOpen: false,
      setSosDialogOpen: vi.fn(),
    };
    return sel(state);
  },
}));

vi.mock("@/hooks/useAssistantUiStore", () => ({
  useAssistantUiStore: (sel: (s: Record<string, unknown>) => unknown) =>
    sel({ setPanelOpen: vi.fn(), setChatOpen: vi.fn() }),
}));

vi.mock("./ShellSosDialog", () => ({
  ShellSosDialog: () => null,
}));

vi.mock("./ShellAccessibilityMenu", () => ({
  ShellAccessibilityMenu: () => (
    <button type="button" aria-label="Accessibility options">
      Accessibility
    </button>
  ),
}));

vi.mock("@/components/brand/ImpiloBrandLogo", () => ({
  ImpiloBrandLogo: () => <span data-testid="logo">logo</span>,
}));

describe("ShellTaskbar", () => {
  it("renders floating workspace dock actions without global account controls", () => {
    render(<ShellTaskbar />);
    expect(screen.getByRole("navigation", { name: /experience shell/i })).toBeInTheDocument();
    expect(screen.getByTestId("shell-floating-dock")).toBeInTheDocument();
    expect(screen.getByLabelText(/start menu/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/open search and commands/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/open nompilo assistant/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/open sos/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/accessibility options/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/open modules/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/profile/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/sign out/i)).not.toBeInTheDocument();
    expect(screen.queryByText("Alerts")).not.toBeInTheDocument();
  });
});
