/**
 * ModeSwitcher — UI component for switching between app modes.
 *
 * Displayed as a compact selector in the header area.
 * Available modes depend on user roles from TSHEPO.
 */

import React, { useCallback } from "react";
import { View, StyleSheet, ScrollView } from "react-native";
import { useAuth } from "@impilo/mobile-auth";
import { Button, colors } from "@impilo/mobile-design-system";
import { appStore, useAppStore } from "../stores/appStore";
import type { AppMode } from "../types";

const MODE_LABELS: Record<AppMode, string> = {
  provider: "Provider",
  outreach: "Outreach",
  supervisor: "Supervisor",
  offline: "Offline Edge",
  courier: "Courier (Nhume)",
};

const MODE_ROLES: Record<AppMode, string[]> = {
  provider: ["provider", "clinician"],
  outreach: ["provider", "clinician", "community_health_worker"],
  supervisor: ["supervisor", "facility_manager", "admin"],
  offline: ["provider", "clinician", "community_health_worker"],
  // Couriers, CHW delivery agents, facility runners, drone/robot operators
  // all unlock the Nhume courier mode. Trust-Layer policies further restrict
  // which deliveries each can act on.
  courier: ["courier", "driver", "facility_runner", "community_health_worker", "drone_operator", "robot_operator", "provider"],
};

function getAvailableModes(roles: string[]): AppMode[] {
  const modes: AppMode[] = [];
  for (const [mode, requiredRoles] of Object.entries(MODE_ROLES)) {
    if (requiredRoles.some((r) => roles.includes(r))) {
      modes.push(mode as AppMode);
    }
  }
  return modes.length > 0 ? modes : ["provider"];
}

export function ModeSwitcher() {
  const auth = useAuth();
  const { mode } = useAppStore();
  const userRoles = auth.user?.realm_access?.roles ?? [];
  const availableModes = getAvailableModes(userRoles);

  const handleModeChange = useCallback((newMode: AppMode) => {
    appStore.getState().setMode(newMode);
  }, []);

  return (
    <View testID="mode-switcher" accessibilityLabel="App mode selector" style={styles.container}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.scrollContent}>
        {availableModes.map((m) => (
          <View key={m} style={styles.buttonWrapper}>
            <Button
              title={MODE_LABELS[m]}
              variant={m === mode ? "primary" : "outline"}
              size="sm"
              onPress={() => handleModeChange(m)}
              testID={`mode-btn-${m}`}
              accessibilityLabel={`Switch to ${MODE_LABELS[m]} mode`}
            />
          </View>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    borderBottomWidth: 1,
    borderBottomColor: colors.gray[200],
    backgroundColor: colors.gray[50],
    paddingVertical: 8,
    paddingHorizontal: 16,
  },
  scrollContent: {
    flexDirection: "row",
    gap: 8,
  },
  buttonWrapper: {
    marginRight: 8,
  },
});
