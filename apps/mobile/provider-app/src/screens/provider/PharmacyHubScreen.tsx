/**
 * PharmacyHubScreen — Tier-2 pharmacy zone hub (dashboard, stock, prescriptions, dispensing).
 */
import React, { useState } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Screen, Header, TabBar, Card, CardBody, Badge, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import { useQuery } from "@tanstack/react-query";
import { fetchPendingDispensing } from "../../services/queueService";
import { PharmacyDispensingScreen } from "./PharmacyDispensingScreen";

type PharmacyTab = "dashboard" | "dispense" | "stock" | "prescriptions";

const TABS: Array<{ key: PharmacyTab; label: string }> = [
  { key: "dashboard", label: "Dashboard" },
  { key: "dispense", label: "Dispense" },
  { key: "stock", label: "Stock" },
  { key: "prescriptions", label: "Prescriptions" },
];

function PharmacyPrescriptionsTab() {
  const { data: pending = [], isLoading } = useQuery({
    queryKey: ["pharmacy-pending"],
    queryFn: fetchPendingDispensing,
  });

  if (isLoading) {
    return <LoadingSpinner />;
  }

  const rows = pending as Array<Record<string, unknown>>;

  return (
    <ScrollView testID="pharmacy-prescriptions-screen" contentContainerStyle={styles.content}>
      <Card>
        <CardBody>
          <Text style={styles.title}>Pending prescriptions ({rows.length})</Text>
          <Text style={styles.sub}>Facility worklist from pharmacy-service — verify then dispense on the Dispense tab.</Text>
        </CardBody>
      </Card>
      {rows.length === 0 ? (
        <Text style={styles.sub}>No pending prescriptions in the worklist.</Text>
      ) : (
        rows.map((item) => (
          <Card key={String(item.id)}>
            <CardBody>
              <Text style={styles.rxName}>{String(item.medication_name ?? "Medication")}</Text>
              <Text style={styles.sub}>
                {String(item.dosage ?? "")} · {String(item.frequency ?? "")} · Patient {String(item.patient_id ?? "")}
              </Text>
              <Badge variant="secondary">{String(item.status ?? "PENDING")}</Badge>
            </CardBody>
          </Card>
        ))
      )}
    </ScrollView>
  );
}

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
      <PharmacyPrescriptionsTab />
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
  title: { fontSize: 16, fontWeight: "700", color: colors.gray[900] },
  sub: { fontSize: 13, color: colors.gray[500], marginTop: 6 },
  rxName: { fontSize: 14, fontWeight: "600", color: colors.gray[900] },
  badges: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginTop: 10 },
});
