/**
 * EquipmentToolsScreen — provider/biomedical/facility-admin equipment hub.
 * Tabbed launcher for the field equipment slice: search, report fault, maintenance.
 * Nompilo guidance surfaced via the shared mobile-nompilo assistant entry.
 */

import React, { useState } from "react";
import { View, Text, StyleSheet, ScrollView, Pressable } from "react-native";
import { EquipmentSearchScreen } from "./EquipmentSearchScreen";
import { MaintenanceTasksScreen } from "./MaintenanceTasksScreen";
import { ReportEquipmentFaultScreen } from "./ReportEquipmentFaultScreen";
import { colors } from "@impilo/mobile-design-system";

type Tab = "search" | "maintenance" | "fault";

export function EquipmentToolsScreen({ facilityId, focusEquipmentId }: { facilityId?: string; focusEquipmentId?: string }) {
  const [tab, setTab] = useState<Tab>("search");

  return (
    <View style={styles.container}>
      <ScrollView horizontal style={styles.tabs} showsHorizontalScrollIndicator={false}>
        <TabButton label="Search" active={tab === "search"} onPress={() => setTab("search")} />
        <TabButton label="Maintenance" active={tab === "maintenance"} onPress={() => setTab("maintenance")} />
        <TabButton label="Report fault" active={tab === "fault"} onPress={() => setTab("fault")} disabled={!focusEquipmentId} />
      </ScrollView>

      <View style={styles.body}>
        {tab === "search" && <EquipmentSearchScreen facilityId={facilityId} />}
        {tab === "maintenance" && <MaintenanceTasksScreen />}
        {tab === "fault" &&
          (focusEquipmentId ? (
            <ReportEquipmentFaultScreen equipmentId={focusEquipmentId} />
          ) : (
            <Text style={styles.hint}>Select an equipment item first to report a fault against it.</Text>
          ))}
      </View>
    </View>
  );
}

function TabButton({ label, active, onPress, disabled }: { label: string; active: boolean; onPress: () => void; disabled?: boolean }) {
  return (
    <Pressable onPress={onPress} disabled={disabled} style={[styles.tab, active && styles.tabActive, disabled && styles.tabDisabled]}>
      <Text style={[styles.tabLabel, active && styles.tabLabelActive]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  tabs: { flexGrow: 0, paddingHorizontal: 8, paddingVertical: 6 },
  tab: { paddingHorizontal: 14, paddingVertical: 8, borderRadius: 999, backgroundColor: "#f3f4f6", marginRight: 8 },
  tabActive: { backgroundColor: "#0f766e" },
  tabDisabled: { opacity: 0.5 },
  tabLabel: { color: colors.gray[700], fontWeight: "600" },
  tabLabelActive: { color: "#ffffff" },
  body: { flex: 1 },
  hint: { padding: 16, color: "#6b7280" },
});
