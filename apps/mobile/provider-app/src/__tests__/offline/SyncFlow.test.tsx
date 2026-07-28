/**
 * Sync Flow Tests — Offline sync lifecycle and queue management.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { appStore } from "../../stores/appStore";

describe("App Store — Offline Sync", () => {
  beforeEach(() => {
    const state = appStore.getState();
    state.setOnlineStatus(true);
    state.setPendingSyncCount(0);
    state.setGlobalError(null);
  });

  it("tracks online/offline status", () => {
    appStore.getState().setOnlineStatus(false);
    expect(appStore.getState().isOnline).toBe(false);

    appStore.getState().setOnlineStatus(true);
    expect(appStore.getState().isOnline).toBe(true);
  });

  it("tracks pending sync count", () => {
    appStore.getState().setPendingSyncCount(5);
    expect(appStore.getState().pendingSyncCount).toBe(5);

    appStore.getState().setPendingSyncCount(0);
    expect(appStore.getState().pendingSyncCount).toBe(0);
  });

  it("manages facility context", () => {
    appStore.getState().setFacilityContext("fac-1", "Test Clinic");
    expect(appStore.getState().facilityId).toBe("fac-1");
    expect(appStore.getState().facilityName).toBe("Test Clinic");
  });

  it("manages workspace context", () => {
    appStore.getState().setWorkspaceContext("ws-1", "OPD");
    expect(appStore.getState().workspaceId).toBe("ws-1");
    expect(appStore.getState().workspaceName).toBe("OPD");
  });

  it("manages mode switching", () => {
    // Ungoverned workspaces — free local navigation, no duty token involved.
    appStore.getState().setMode("outreach");
    expect(appStore.getState().mode).toBe("outreach");

    appStore.getState().setMode("offline");
    expect(appStore.getState().mode).toBe("offline");

    appStore.getState().setMode("courier");
    expect(appStore.getState().mode).toBe("courier");

    // Governed workspaces go through setGrantedMode, which useSwitchAppMode
    // only reaches after a successful mint. `setMode("supervisor")` is now a
    // compile error — that is the guard, not a convention.
    appStore.getState().setGrantedMode("supervisor");
    expect(appStore.getState().mode).toBe("supervisor");

    appStore.getState().setGrantedMode("provider");
    expect(appStore.getState().mode).toBe("provider");
  });

  it("manages global errors", () => {
    appStore.getState().setGlobalError({ code: "NETWORK_ERROR", message: "Connection lost" });
    expect(appStore.getState().globalError?.code).toBe("NETWORK_ERROR");

    appStore.getState().setGlobalError(null);
    expect(appStore.getState().globalError).toBeNull();
  });

  it("clears context on logout", () => {
    appStore.getState().setFacilityContext("fac-1", "Test");
    appStore.getState().setWorkspaceContext("ws-1", "OPD");
    appStore.getState().setShiftId("shift-1");

    appStore.getState().clearContext();
    expect(appStore.getState().facilityId).toBeNull();
    expect(appStore.getState().workspaceId).toBeNull();
    expect(appStore.getState().shiftId).toBeNull();
  });

  it("tracks notification count", () => {
    appStore.getState().setUnreadNotifications(7);
    expect(appStore.getState().unreadNotifications).toBe(7);
  });
});
