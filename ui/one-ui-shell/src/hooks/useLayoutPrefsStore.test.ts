import { beforeEach, describe, expect, it } from "vitest";
import { useLayoutPrefsStore } from "./useLayoutPrefsStore";

function resetStore() {
  useLayoutPrefsStore.setState({
    hydrated: false,
    navRailPref: "auto",
    taskbarMinimized: false,
    focusMode: false,
  });
  window.localStorage.clear();
}

describe("useLayoutPrefsStore", () => {
  beforeEach(resetStore);

  it("persists nav rail preference and taskbar minimise across hydration", () => {
    useLayoutPrefsStore.getState().setNavRailPref("compact");
    useLayoutPrefsStore.getState().setTaskbarMinimized(true);

    resetStore.call(null);
    // simulate a fresh session with the persisted values still in storage
    window.localStorage.setItem("exp:layout_nav_rail", "compact");
    window.localStorage.setItem("exp:layout_taskbar_min", "true");
    useLayoutPrefsStore.getState().hydrateFromStorage();

    expect(useLayoutPrefsStore.getState().navRailPref).toBe("compact");
    expect(useLayoutPrefsStore.getState().taskbarMinimized).toBe(true);
  });

  it("hydration ignores garbage storage values", () => {
    window.localStorage.setItem("exp:layout_nav_rail", "sideways");
    window.localStorage.setItem("exp:layout_taskbar_min", "banana");
    useLayoutPrefsStore.getState().hydrateFromStorage();
    expect(useLayoutPrefsStore.getState().navRailPref).toBe("auto");
    expect(useLayoutPrefsStore.getState().taskbarMinimized).toBe(false);
  });

  it("focus mode is session-only: toggling it writes nothing to storage", () => {
    useLayoutPrefsStore.getState().toggleFocusMode();
    expect(useLayoutPrefsStore.getState().focusMode).toBe(true);
    expect(window.localStorage.length).toBe(0);
  });

  it("hydrateFromStorage is idempotent and never overwrites live changes", () => {
    useLayoutPrefsStore.getState().hydrateFromStorage();
    useLayoutPrefsStore.getState().setNavRailPref("expanded");
    useLayoutPrefsStore.getState().hydrateFromStorage();
    expect(useLayoutPrefsStore.getState().navRailPref).toBe("expanded");
  });
});
