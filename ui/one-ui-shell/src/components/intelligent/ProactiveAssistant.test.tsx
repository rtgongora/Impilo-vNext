import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProactiveAssistant } from "./ProactiveAssistant";
import {
  TYPING_SUPPRESSION_MS,
  shouldSuppressNonUrgent,
  useAssistantUiStore,
} from "@/hooks/useAssistantUiStore";
import { useLayoutPrefsStore } from "@/hooks/useLayoutPrefsStore";

const mockPathname = vi.fn(() => "/home");

vi.mock("next/navigation", () => ({
  usePathname: () => mockPathname(),
}));

vi.mock("@/hooks/useWorkModeStore", () => ({
  useWorkModeStore: () => ({ mode: "clinical" }),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: () => ({ facility: null }),
}));

vi.mock("@/hooks/useShiftStore", () => ({
  useShiftStore: () => ({ shift: null }),
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get: vi.fn(async () => ({ data: [] })),
    post: vi.fn(async () => ({ data: { reply: "hello from nompilo" } })),
  },
}));

function resetStores() {
  useAssistantUiStore.setState({ panelOpen: false, chatOpen: false, chatMessages: [], lastTypingAt: 0 });
  useLayoutPrefsStore.setState({ hydrated: true, navRailPref: "auto", taskbarMinimized: false, focusMode: false });
}

describe("Nompilo interruption rules", () => {
  it("suppresses non-urgent prompts only within the active-typing window", () => {
    const now = 100_000;
    expect(shouldSuppressNonUrgent(0, now)).toBe(false);
    expect(shouldSuppressNonUrgent(now - TYPING_SUPPRESSION_MS + 1, now)).toBe(true);
    expect(shouldSuppressNonUrgent(now - TYPING_SUPPRESSION_MS - 1, now)).toBe(false);
  });
});

describe("ProactiveAssistant", () => {
  beforeEach(resetStores);

  it("retains the conversation when minimised and remounted (route change)", () => {
    useAssistantUiStore.getState().appendChatMessage({ role: "user", text: "check bed state" });
    useAssistantUiStore.getState().setChatOpen(true);

    const first = render(<ProactiveAssistant />);
    fireEvent.click(screen.getByTestId("proactive-assistant-launcher"));
    expect(screen.getByText("check bed state")).toBeInTheDocument();

    // minimise, unmount (navigation), remount — conversation survives
    fireEvent.click(screen.getByLabelText("Minimize assistant (conversation is kept)"));
    first.unmount();
    render(<ProactiveAssistant />);
    fireEvent.click(screen.getByTestId("proactive-assistant-launcher"));
    expect(screen.getByText("check bed state")).toBeInTheDocument();
  });

  it("never auto-opens the panel and shrinks the launcher during focused work", () => {
    mockPathname.mockReturnValue("/pharmacy/dispense");
    render(<ProactiveAssistant />);
    const launcher = screen.getByTestId("proactive-assistant-launcher");
    expect(launcher).toHaveAttribute("data-minimized", "true");
    expect(launcher).toHaveAttribute("aria-expanded", "false");
    // a deliberate tap still opens it
    fireEvent.click(launcher);
    expect(useAssistantUiStore.getState().panelOpen).toBe(true);
    expect(launcher).toHaveAttribute("data-minimized", "false");
    mockPathname.mockReturnValue("/home");
  });

  it("keeps the full-size launcher on navigation surfaces", () => {
    mockPathname.mockReturnValue("/home");
    render(<ProactiveAssistant />);
    expect(screen.getByTestId("proactive-assistant-launcher")).toHaveAttribute("data-minimized", "false");
  });
});
