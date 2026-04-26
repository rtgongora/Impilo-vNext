/**
 * PharmacyHubScreen — Tier-2 pharmacy zone hub (dashboard, stock, prescriptions, dispensing).
 */
import React, { useState } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Screen, Header, TabBar, Card, CardBody, Badge } from "@impilo/mobile-design-system";
import { PharmacyDispensingScreen } from "./PharmacyDispensingScreen";

type PharmacyTab = "dashboard" | "dispense" | "stock" | "prescriptions";

const TABS: Array<{ key: PharmacyTab; label: string }> = [
  { key: "dashboard", label: "Dashboard" },
  { key: "dispense", label: "Dispense" },
  { key: "stock", label: "Stock" },
  { key: "prescriptions", label: "Prescriptions" },
];

export function PharmacyHubScreen() {
  const [tab, setTab] = useState<PharmacyTab>("dashboard");

  const body =
    tab === "dispense" ? (
      <PharmacyDispensingScreen />
    ) : tab === "dashboard" ? (
      <ScrollView testID="pharmacy-dashboard-screen" contentContainerStyle={styles.content}>
        <Card>
          <CardBody>
            <Text style={styles.title}>Pharmacy Dashboard</Text>
            <Text style={styles.sub}>Tier-2 parity: dashboard + stock + prescriptions.</Text>
            <View style={styles.badges}>
              <Badge variant="secondary">Dispensing</Badge>
              <Badge variant="outline">Stock</Badge>
              <Badge variant="outline">Prescriptions</Badge>
            </View>
          </CardBody>
        </Card>
      </ScrollView>
    ) : tab === "stock" ? (
      <ScrollView testID="pharmacy-stock-screen" contentContainerStyle={styles.content}>
        <Card>
          <CardBody>
            <Text style={styles.title}>Stock Management</Text>
            <Text style={styles.sub}>Inventory controls will be wired to the pharmacy service.</Text>
          </CardBody>
        </Card>
      </ScrollView>
    ) : (
      <ScrollView testID="pharmacy-prescriptions-screen" contentContainerStyle={styles.content}>
        <Card>
          <CardBody>
            <Text style={styles.title}>Prescriptions</Text>
            <Text style={styles.sub}>View and manage prescription pipeline (Tier-2).</Text>
          </CardBody>
        </Card>
      </ScrollView>
    );

  return (
    <Screen>
      <Header title="Pharmacy" />
      <View style={styles.tabWrap}>
        <TabBar items={TABS} activeKey={tab} onSelect={(k) => setTab(k as PharmacyTab)} />
      </View>
      {body}
    </Screen>
  );
}

const styles = StyleSheet.create({
  tabWrap: { paddingHorizontal: 12, paddingTop: 8 },
  content: { padding: 16, gap: 12 },
  title: { fontSize: 16, fontWeight: "700", color: "#111827" },
  sub: { fontSize: 13, color: "#6B7280", marginTop: 6 },
  badges: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginTop: 10 },
});

