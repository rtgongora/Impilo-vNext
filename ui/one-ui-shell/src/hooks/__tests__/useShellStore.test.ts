import { describe, it, expect, beforeEach, vi } from "vitest";
import { useShellStore } from "../useShellStore";

const sessionStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value;
    }),
    removeItem: vi.fn((key: string) => {
      delete store[key];
    }),
    clear: vi.fn(() => {
      store = {};
    }),
    get length() {
      return Object.keys(store).length;
    },
    key: vi.fn((index: number) => Object.keys(store)[index] ?? null),
  };
})();

Object.defineProperty(global, "sessionStorage", { value: sessionStorageMock });

describe("useShellStore", () => {
  beforeEach(() => {
    useShellStore.getState().clearSessionShellState();
    sessionStorageMock.clear();
    vi.clearAllMocks();
  });

  it("clearClosedTasks removes minimized tasks only", () => {
    const t1 = {
      id: "open-1",
      appId: "a",
      taskType: "module" as const,
      title: "Open",
      route: "/home",
      openedAt: "2026-01-01T00:00:00.000Z",
      lastActiveAt: "2026-01-01T00:00:00.000Z",
      status: "open" as const,
    };
    const t2 = {
      id: "min-1",
      appId: "a",
      taskType: "module" as const,
      title: "Min",
      route: "/queue",
      openedAt: "2026-01-01T00:00:00.000Z",
      lastActiveAt: "2026-01-01T00:00:00.000Z",
      status: "minimized" as const,
    };
    useShellStore.setState({ openTasks: [t1, t2], activeTaskId: "min-1" });
    useShellStore.getState().clearClosedTasks();
    const { openTasks, activeTaskId } = useShellStore.getState();
    expect(openTasks).toHaveLength(1);
    expect(openTasks[0].id).toBe("open-1");
    expect(activeTaskId).toBe("open-1");
  });

  it("applyRemoteWorkspaceSnapshot replaces pins and recents", () => {
    const r = {
      id: "r1",
      kind: "route" as const,
      title: "Home",
      href: "/home",
      refKey: "route:/home",
      recordedAt: "2026-01-01T00:00:00.000Z",
    };
    useShellStore.getState().applyRemoteWorkspaceSnapshot({ pinnedAppCodes: ["home"], recentItems: [r] });
    const s = useShellStore.getState();
    expect(s.pinnedAppCodes).toEqual(["home"]);
    expect(s.recentItems).toHaveLength(1);
    expect(s.recentItems[0].refKey).toBe("route:/home");
  });

  it("mergeRemoteWorkspaceWithSession unions pins (local order first)", () => {
    useShellStore.setState({ pinnedAppCodes: ["a", "b"], recentItems: [] });
    useShellStore.getState().mergeRemoteWorkspaceWithSession({ pinnedAppCodes: ["c", "a"], recentItems: [] });
    expect(useShellStore.getState().pinnedAppCodes).toEqual(["a", "b", "c"]);
  });

  it("mergeRemoteWorkspaceWithSession picks newer recordedAt per refKey", () => {
    const local = {
      id: "1",
      kind: "route" as const,
      title: "Old",
      href: "/x",
      refKey: "route:/x",
      recordedAt: "2026-01-01T00:00:00.000Z",
    };
    const remote = {
      id: "2",
      kind: "route" as const,
      title: "New",
      href: "/x",
      refKey: "route:/x",
      recordedAt: "2026-02-01T00:00:00.000Z",
    };
    useShellStore.setState({ pinnedAppCodes: [], recentItems: [local] });
    useShellStore.getState().mergeRemoteWorkspaceWithSession({ pinnedAppCodes: [], recentItems: [remote] });
    const r = useShellStore.getState().recentItems.find((x) => x.refKey === "route:/x");
    expect(r?.title).toBe("New");
    expect(r?.id).toBe("2");
  });

  it("mergeRemoteWorkspaceWithSession keeps local on exact recordedAt tie", () => {
    const ts = "2026-01-15T12:00:00.000Z";
    const local = {
      id: "local-id",
      kind: "route" as const,
      title: "Local",
      href: "/y",
      refKey: "route:/y",
      recordedAt: ts,
    };
    const remote = {
      id: "remote-id",
      kind: "route" as const,
      title: "Remote",
      href: "/y",
      refKey: "route:/y",
      recordedAt: ts,
    };
    useShellStore.setState({ pinnedAppCodes: [], recentItems: [local] });
    useShellStore.getState().mergeRemoteWorkspaceWithSession({ pinnedAppCodes: [], recentItems: [remote] });
    const r = useShellStore.getState().recentItems.find((x) => x.refKey === "route:/y");
    expect(r?.title).toBe("Local");
    expect(r?.id).toBe("local-id");
  });

  it("focusSearchPalette opens search and bumps focus tick", () => {
    useShellStore.getState().focusSearchPalette();
    const s = useShellStore.getState();
    expect(s.searchOpen).toBe(true);
    expect(s.startOpen).toBe(false);
    expect(s.searchPaletteFocusTick).toBe(1);
    useShellStore.getState().focusSearchPalette();
    expect(useShellStore.getState().searchPaletteFocusTick).toBe(2);
  });

  it("opening the nav drawer closes search, start, and SOS", () => {
    useShellStore.setState({ searchOpen: true, startOpen: true, sosDialogOpen: true });
    useShellStore.getState().setNavDrawerOpen(true);
    const s = useShellStore.getState();
    expect(s.navDrawerOpen).toBe(true);
    expect(s.searchOpen).toBe(false);
    expect(s.startOpen).toBe(false);
    expect(s.sosDialogOpen).toBe(false);
  });
});
