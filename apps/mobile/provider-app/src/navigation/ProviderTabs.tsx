/**
 * ProviderTabs — Tab navigator for Provider mode.
 *
 * Tabs: Dashboard (Worklist), Patients, Results, Activity, Professional
 */

import React, { useCallback, useEffect } from "react";
import { View, StyleSheet } from "react-native";
import { TabBar } from "@impilo/mobile-design-system";
import { ProviderDashboardScreen } from "../screens/provider/ProviderDashboardScreen";
import { PatientLookupScreen } from "../screens/provider/PatientLookupScreen";
import { ResultsViewScreen } from "../screens/provider/ResultsViewScreen";
import { ProfessionalProfileScreen } from "../screens/provider/ProfessionalProfileScreen";
import { QueueManagementScreen } from "../screens/provider/QueueManagementScreen";
import { ClinicalToolsScreen } from "../screens/provider/ClinicalToolsScreen";
import { EncounterScreen } from "../screens/provider/EncounterScreen";
import { useAppStore } from "../stores/appStore";
import { useEncounterStore } from "../stores/encounterStore";
import type { ProviderTabKey } from "../types";

export function ProviderTabs() {
  const { unreadNotifications, providerTab, setProviderTab } = useAppStore();
  const { activeEncounter } = useEncounterStore();

  const tabs = [
    { key: "dashboard", label: "Launch", icon: "clipboard" },
    { key: "patients", label: "Patients", icon: "users" },
    ...(activeEncounter ? [{ key: "encounter", label: "Encounter", icon: "heart-pulse" }] : []),
    { key: "results", label: "Results", icon: "activity" },
    { key: "queue", label: "Queue", icon: "list" },
    { key: "tools", label: "Tools", icon: "briefcase" },
    { key: "professional", label: "Profile", icon: "user" },
  ] as const;

  useEffect(() => {
    if (!activeEncounter && providerTab === "encounter") {
      setProviderTab("dashboard");
    }
  }, [activeEncounter, providerTab, setProviderTab]);

  const handleTabChange = useCallback((key: string) => {
    setProviderTab(key as ProviderTabKey);
  }, [setProviderTab]);

  const renderContent = () => {
    switch (providerTab) {
      case "dashboard":
        return <ProviderDashboardScreen />;
      case "patients":
        return <PatientLookupScreen />;
      case "encounter":
        return <EncounterScreen />;
      case "results":
        return <ResultsViewScreen />;
      case "queue":
        return <QueueManagementScreen />;
      case "tools":
        return <ClinicalToolsScreen />;
      case "professional":
        return <ProfessionalProfileScreen />;
      default:
        return <ProviderDashboardScreen />;
    }
  };

  return (
    <View testID="provider-tabs" style={styles.container}>
      <View style={styles.content}>{renderContent()}</View>
      <TabBar
        items={tabs.map((t) => ({
          key: t.key,
          label: t.label,
          icon: t.icon,
          badge: t.key === "dashboard" && unreadNotifications > 0 ? unreadNotifications : undefined,
        }))}
        activeKey={providerTab}
        onSelect={handleTabChange}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    flex: 1,
  },
});
