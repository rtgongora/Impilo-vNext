/**
 * ProviderTabs — Tab navigator for Provider mode.
 *
 * Tabs: Dashboard (Worklist), Patients, Results, Activity, Professional
 */

import React, { useState, useCallback } from "react";
import { View, StyleSheet } from "react-native";
import { TabBar } from "@impilo/mobile-design-system";
import { ProviderDashboardScreen } from "../screens/provider/ProviderDashboardScreen";
import { PatientLookupScreen } from "../screens/provider/PatientLookupScreen";
import { ActivityFeedScreen } from "../screens/provider/ActivityFeedScreen";
import { ResultsViewScreen } from "../screens/provider/ResultsViewScreen";
import { ProfessionalProfileScreen } from "../screens/provider/ProfessionalProfileScreen";
import { QueueManagementScreen } from "../screens/provider/QueueManagementScreen";
import { BedManagementScreen } from "../screens/provider/BedManagementScreen";
import { PharmacyDispensingScreen } from "../screens/provider/PharmacyDispensingScreen";
import { ClinicalToolsScreen } from "../screens/provider/ClinicalToolsScreen";
import { useAppStore } from "../stores/appStore";

const TABS = [
  { key: "dashboard", label: "Worklist", icon: "clipboard" },
  { key: "patients", label: "Patients", icon: "users" },
  { key: "results", label: "Results", icon: "activity" },
  { key: "queue", label: "Queue", icon: "list" },
  { key: "tools", label: "Tools", icon: "briefcase" },
  { key: "professional", label: "Profile", icon: "user" },
] as const;

type TabKey = (typeof TABS)[number]["key"];

export function ProviderTabs() {
  const [activeTab, setActiveTab] = useState<TabKey>("dashboard");
  const { unreadNotifications } = useAppStore();

  const handleTabChange = useCallback((key: string) => {
    setActiveTab(key as TabKey);
  }, []);

  const renderContent = () => {
    switch (activeTab) {
      case "dashboard":
        return <ProviderDashboardScreen />;
      case "patients":
        return <PatientLookupScreen />;
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
        items={TABS.map((t) => ({
          key: t.key,
          label: t.label,
          icon: t.icon,
          badge: t.key === "dashboard" && unreadNotifications > 0 ? unreadNotifications : undefined,
        }))}
        activeKey={activeTab}
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
