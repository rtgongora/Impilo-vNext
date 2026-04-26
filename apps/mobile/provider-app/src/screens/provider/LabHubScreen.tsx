/**
 * LabHubScreen — Tier-2 lab zone hub (worklist, catalog, reconciliation, results).
 */
import React, { useState } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Screen, Header, TabBar, Card, CardBody } from "@impilo/mobile-design-system";
import { ResultsViewScreen } from "./ResultsViewScreen";

type LabTab = "worklist" | "results" | "catalog" | "reconciliation";

const TABS: Array<{ key: LabTab; label: string }> = [
  { key: "worklist", label: "Worklist" },
  { key: "results", label: "Results" },
  { key: "catalog", label: "Catalog" },
  { key: "reconciliation", label: "Reconcile" },
];

export function LabHubScreen() {
  const [tab, setTab] = useState<LabTab>("results");

  const body =
    tab === "results" ? (
      <ResultsViewScreen />
    ) : (
      <ScrollView
        testID={
          tab === "worklist"
            ? "lab-worklist-screen"
            : tab === "catalog"
              ? "lab-catalog-screen"
              : "lab-reconciliation-screen"
        }
        contentContainerStyle={styles.content}
      >
        <Card>
          <CardBody>
            <Text style={styles.title}>
              {tab === "worklist"
                ? "Lab Worklist"
                : tab === "catalog"
                  ? "Test Catalog"
                  : "Lab Reconciliation"}
            </Text>
            <Text style={styles.sub}>
              Tier-2 parity surface. Wiring to sovereign lab services happens next.
            </Text>
          </CardBody>
        </Card>
      </ScrollView>
    );

  return (
    <Screen>
      <Header title="Laboratory" />
      <View style={styles.tabWrap}>
        <TabBar items={TABS} activeKey={tab} onSelect={(k) => setTab(k as LabTab)} />
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
});

