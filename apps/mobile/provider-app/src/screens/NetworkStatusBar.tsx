/**
 * NetworkStatusBar — Shows offline/online status.
 *
 * Listens to browser online/offline events and syncs with appStore.
 */

import React, { useEffect } from "react";
import { StatusIndicator } from "@impilo/mobile-design-system";
import { useAppStore, appStore } from "../stores/appStore";
import { useSyncEngine } from "@impilo/mobile-offline";

export function NetworkStatusBar() {
  const { isOnline, pendingSyncCount } = useAppStore();
  const { status: syncStatus } = useSyncEngine();

  useEffect(() => {
    const handleOnline = () => appStore.getState().setOnlineStatus(true);
    const handleOffline = () => appStore.getState().setOnlineStatus(false);

    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);

    // Set initial state
    appStore.getState().setOnlineStatus(navigator.onLine);

    return () => {
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, []);

  if (isOnline && syncStatus === "idle" && pendingSyncCount === 0) {
    return null;
  }

  const statusText = !isOnline
    ? "Offline — changes will sync when connected"
    : syncStatus === "syncing"
      ? `Syncing ${pendingSyncCount} items...`
      : pendingSyncCount > 0
        ? `${pendingSyncCount} items pending sync`
        : "";

  if (!statusText) return null;

  return React.createElement(
    "div",
    {
      "data-testid": "network-status-bar",
      style: {
        backgroundColor: isOnline ? "#FEF3C7" : "#FEE2E2",
        padding: "8px 16px",
        display: "flex",
        alignItems: "center",
        gap: "8px",
        fontSize: "14px",
      },
    },
    React.createElement(StatusIndicator, {
      status: isOnline ? "syncing" : "offline",
      size: "sm",
    }),
    React.createElement("span", null, statusText)
  );
}
