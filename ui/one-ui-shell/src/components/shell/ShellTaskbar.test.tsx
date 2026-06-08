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

vi.mock("./ShellSosDialog", () => ({
  ShellSosDialog: () => null,
}));

vi.mock("./ShellNotificationTray", () => ({
  ShellNotificationTray: () => <span data-testid="notif-tray">tray</span>,
}));

vi.mock("@/components/brand/ImpiloBrandLogo", () => ({
  ImpiloBrandLogo: () => <span data-testid="logo">logo</span>,
}));

describe("ShellTaskbar", () => {
  it("renders universal shell actions with accessible names", () => {
    render(<ShellTaskbar />);
    expect(screen.getByRole("navigation", { name: /experience shell/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/start menu/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/open search and commands/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/open nompilo ask/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/open sos/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/profile/i)).toBeInTheDocument();
  });
});
