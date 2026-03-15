/**
 * ModeRouter — Routes to the active mode's tab navigator.
 *
 * Reads the current mode from appStore and renders the corresponding
 * mode component. Also renders the mode switcher overlay.
 */

import React, { useCallback } from "react";
import { useAppStore } from "../stores/appStore";
import { ProviderTabs } from "./ProviderTabs";
import { OutreachTabs } from "./OutreachTabs";
import { SupervisorTabs } from "./SupervisorTabs";
import { OfflineTabs } from "./OfflineTabs";
import { ModeSwitcher } from "./ModeSwitcher";
import type { AppMode } from "../types";

export function ModeRouter() {
  const { mode } = useAppStore();

  const renderMode = useCallback(() => {
    switch (mode) {
      case "provider":
        return React.createElement(ProviderTabs, null);
      case "outreach":
        return React.createElement(OutreachTabs, null);
      case "supervisor":
        return React.createElement(SupervisorTabs, null);
      case "offline":
        return React.createElement(OfflineTabs, null);
      default:
        return React.createElement(ProviderTabs, null);
    }
  }, [mode]);

  return React.createElement(
    "div",
    { "data-testid": "mode-router", "data-mode": mode },
    React.createElement(ModeSwitcher, null),
    renderMode()
  );
}
