/**
 * ModeRouter — Routes to the active mode's tab navigator.
 *
 * Reads the current mode from appStore and renders the corresponding
 * mode component. Also renders the mode switcher overlay.
 */

import React, { useCallback } from "react";
import { View, StyleSheet } from "react-native";
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
        return <ProviderTabs />;
      case "outreach":
        return <OutreachTabs />;
      case "supervisor":
        return <SupervisorTabs />;
      case "offline":
        return <OfflineTabs />;
      default:
        return <ProviderTabs />;
    }
  }, [mode]);

  return (
    <View testID="mode-router" style={styles.container} data-mode={mode}>
      <ModeSwitcher />
      {renderMode()}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
});
