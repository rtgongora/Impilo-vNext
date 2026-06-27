/**
 * ProviderTabs — Tab navigator for Provider mode.
 *
 * Tabs: Dashboard (Worklist), Patients, Results, Queue, Tools, Professional
 */

import React, { useCallback, useEffect } from "react";
import { View, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { TabBar } from "@impilo/mobile-design-system";
import { ProviderDashboardScreen } from "../screens/provider/ProviderDashboardScreen";
import { PatientLookupScreen } from "../screens/provider/PatientLookupScreen";
import { ResultsViewScreen } from "../screens/provider/ResultsViewScreen";
import { ProfessionalProfileScreen } from "../screens/provider/ProfessionalProfileScreen";
import { QueueManagementScreen } from "../screens/provider/QueueManagementScreen";
import { ClinicalToolsScreen } from "../screens/provider/ClinicalToolsScreen";
import { EncounterScreen } from "../screens/provider/EncounterScreen";
import { MessagingScreen } from "../screens/provider/MessagingScreen";
import { HealthOsAppsScreen } from "../screens/provider/HealthOsAppsScreen";
import { ProviderSocialScreen } from "../screens/provider/ProviderSocialScreen";
import { DiagnosticsScreen } from "../screens/provider/DiagnosticsScreen";
import { useAppStore } from "../stores/appStore";
import { useEncounterStore } from "../stores/encounterStore";
import type { ProviderTabKey } from "../types";

const ACCENT = "#1E40AF";

function tabIcon(name: string, isActive: boolean): React.ReactNode {
  const color = isActive ? ACCENT : "#9CA3AF";
  return <Ionicons name={name as never} size={22} color={color} />;
}

export function ProviderTabs() {
  const { unreadNotifications, providerTab, setProviderTab } = useAppStore();
  const { activeEncounter } = useEncounterStore();

  useEffect(() => {
    if (!activeEncounter && providerTab === "encounter") {
      setProviderTab("dashboard");
    }
  }, [activeEncounter, providerTab, setProviderTab]);

  const handleTabChange = useCallback((key: string) => {
    setProviderTab(key as ProviderTabKey);
  }, [setProviderTab]);

  const tabs = [
    {
      key: "dashboard" as const,
      label: "Work",
      icon: tabIcon(providerTab === "dashboard" ? "briefcase" : "briefcase-outline", providerTab === "dashboard"),
      badge: unreadNotifications > 0 ? unreadNotifications : undefined,
    },
    {
      key: "professional" as const,
      label: "My Professional",
      icon: tabIcon(providerTab === "professional" ? "id-card" : "id-card-outline", providerTab === "professional"),
    },
    {
      key: "patients" as const,
      label: "Patients",
      icon: tabIcon(providerTab === "patients" ? "people" : "people-outline", providerTab === "patients"),
    },
    ...(activeEncounter
      ? [
          {
            key: "encounter" as const,
            label: "Encounter",
            icon: tabIcon(providerTab === "encounter" ? "pulse" : "pulse-outline", providerTab === "encounter"),
          },
        ]
      : []),
    {
      key: "diagnostics" as const,
      label: "Diagnostics",
      icon: tabIcon(providerTab === "diagnostics" ? "scan" : "scan-outline", providerTab === "diagnostics"),
    },
    {
      key: "results" as const,
      label: "Results",
      icon: tabIcon(providerTab === "results" ? "flask" : "flask-outline", providerTab === "results"),
    },
    {
      key: "queue" as const,
      label: "Queue",
      icon: tabIcon(providerTab === "queue" ? "list" : "list-outline", providerTab === "queue"),
    },
    {
      key: "messaging" as const,
      label: "Messages",
      icon: tabIcon(providerTab === "messaging" ? "chatbubbles" : "chatbubbles-outline", providerTab === "messaging"),
    },
    {
      key: "social" as const,
      label: "Network",
      icon: tabIcon(providerTab === "social" ? "people-circle" : "people-circle-outline", providerTab === "social"),
    },
    {
      key: "tools" as const,
      label: "Tools",
      icon: tabIcon(providerTab === "tools" ? "construct" : "construct-outline", providerTab === "tools"),
    },
    {
      key: "apps" as const,
      label: "Training",
      icon: tabIcon(providerTab === "apps" ? "school" : "school-outline", providerTab === "apps"),
    },
  ];

  const renderContent = () => {
    switch (providerTab) {
      case "dashboard":
        return <ProviderDashboardScreen />;
      case "patients":
        return <PatientLookupScreen />;
      case "encounter":
        return <EncounterScreen />;
      case "diagnostics":
        return <DiagnosticsScreen />;
      case "results":
        return <ResultsViewScreen />;
      case "queue":
        return <QueueManagementScreen />;
      case "messaging":
        return <MessagingScreen />;
      case "social":
        return <ProviderSocialScreen />;
      case "tools":
        return <ClinicalToolsScreen />;
      case "apps":
        return <HealthOsAppsScreen />;
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
        items={tabs}
        activeKey={providerTab}
        onSelect={handleTabChange}
        accentColor={ACCENT}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#F8FAFC",
  },
  content: {
    flex: 1,
  },
});
