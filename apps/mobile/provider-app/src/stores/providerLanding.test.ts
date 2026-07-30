/**
 * The provider landing surface (Phase G4 close-out).
 *
 * Mobile now lands on Work Home rather than the Worklist, matching web's F6
 * flip from /provider-workspace to /work. Pinned as its own test because this
 * is a doctrine decision — "one experience shell, role- and context-aware" —
 * and a silent revert to a bare worklist would be invisible in a diff that
 * only touched a string.
 *
 * Worklist is not lost: it stays the second tab, one tap away, and remains
 * where an in-progress encounter returns you.
 */
import { describe, expect, it, beforeEach } from "vitest";
import { appStore } from "./appStore";

describe("provider landing surface", () => {
  beforeEach(() => {
    appStore.setState({ mode: "provider", providerTab: "workhome" });
  });

  it("starts on Work Home, not the bare worklist", () => {
    expect(appStore.getInitialState().providerTab).toBe("workhome");
  });

  it("returns to Work Home whenever the clinician re-enters provider mode", () => {
    appStore.getState().setProviderTab("messaging");
    appStore.getState().setGrantedMode("provider");
    expect(appStore.getState().providerTab).toBe("workhome");
  });

  it("leaves the tab alone when entering a non-provider workspace", () => {
    appStore.getState().setProviderTab("results");
    appStore.getState().setGrantedMode("outreach");
    expect(appStore.getState().providerTab).toBe("results");
  });

  it("still allows the worklist to be selected directly", () => {
    appStore.getState().setProviderTab("dashboard");
    expect(appStore.getState().providerTab).toBe("dashboard");
  });
});
