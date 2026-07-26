/**
 * BudgetSummaryScreen — Provider/finance mobile slice for Intelligent Budgeting.
 *
 * Lists managed budgets, opens a budget's budget-vs-actual variance (with the
 * explainable classification + driver note), and offers the approval task for
 * budgets that are under review. Real data via the BFF → COSTA; segregation of
 * duties and scope are enforced server-side.
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView, ActivityIndicator, TouchableOpacity } from "react-native";
import { Card, CardHeader, CardBody, Button, Badge, colors } from "@impilo/mobile-design-system";
import {
  listBudgets,
  getVariance,
  approveBudget,
  isApprovable,
  type BudgetSummary,
  type BudgetVariance,
} from "../../services/budgetService";

function money(v: number | null | undefined, currency: string): string {
  if (v == null) return "—";
  return `${currency} ${Number(v).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

const CLASS_VARIANT: Record<string, "success" | "warning" | "error" | "info"> = {
  ON_TRACK: "success",
  FAVOURABLE: "success",
  UNFAVOURABLE_MINOR: "warning",
  UNFAVOURABLE_MAJOR: "warning",
  OVERSPENT: "error",
  UNDERSPENT_STALE: "info",
};

export function BudgetSummaryScreen() {
  const year = new Date().getFullYear();
  const [budgets, setBudgets] = useState<BudgetSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<BudgetSummary | null>(null);
  const [variance, setVariance] = useState<BudgetVariance | null>(null);
  const [varianceLoading, setVarianceLoading] = useState(false);
  const [approving, setApproving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setBudgets(await listBudgets(year));
    } catch {
      setError("Could not load budgets. Pull to retry.");
    } finally {
      setLoading(false);
    }
  }, [year]);

  useEffect(() => {
    void load();
  }, [load]);

  const openBudget = useCallback(async (b: BudgetSummary) => {
    setSelected(b);
    setVariance(null);
    setVarianceLoading(true);
    try {
      setVariance(await getVariance(b.budgetId));
    } catch {
      setVariance(null);
    } finally {
      setVarianceLoading(false);
    }
  }, []);

  const onApprove = useCallback(async () => {
    if (!selected) return;
    setApproving(true);
    try {
      await approveBudget(selected.budgetId);
      setSelected(null);
      await load();
    } catch {
      setError("Approval was blocked (role or segregation of duties).");
    } finally {
      setApproving(false);
    }
  }, [selected, load]);

  if (loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator />
        <Text style={styles.muted}>Loading budgets…</Text>
      </View>
    );
  }

  if (selected) {
    return (
      <ScrollView contentContainerStyle={styles.container}>
        <TouchableOpacity onPress={() => setSelected(null)}>
          <Text style={styles.link}>‹ All budgets</Text>
        </TouchableOpacity>
        <Card>
          <CardHeader>
            <Text style={styles.title}>{selected.title}</Text>
            <Text style={styles.muted}>
              {selected.scopeLevel} · FY{selected.periodYear} · {selected.status}
            </Text>
          </CardHeader>
          <CardBody>
            {varianceLoading ? (
              <ActivityIndicator />
            ) : variance ? (
              <View>
                <View style={styles.row}><Text style={styles.muted}>Budgeted</Text><Text>{money(variance.budgeted, selected.currency)}</Text></View>
                <View style={styles.row}><Text style={styles.muted}>Committed</Text><Text>{money(variance.committed, selected.currency)}</Text></View>
                <View style={styles.row}><Text style={styles.muted}>Actual</Text><Text>{money(variance.actual, selected.currency)}</Text></View>
                <View style={styles.row}><Text style={styles.muted}>Available</Text><Text>{money(variance.available, selected.currency)}</Text></View>
                <View style={styles.badgeRow}>
                  <Badge variant={CLASS_VARIANT[variance.classification] ?? "info"}>
                    {variance.classification.replace(/_/g, " ")}
                  </Badge>
                </View>
                <Text style={styles.driver}>{variance.driverNote}</Text>
                <Text style={styles.muted}>As of {variance.asOfDate}</Text>
              </View>
            ) : (
              <Text style={styles.muted}>No variance data available.</Text>
            )}

            {isApprovable(selected.status) && (
              <View style={styles.approve}>
                <Button
                  title={approving ? "Approving…" : "Approve budget"}
                  onPress={onApprove}
                  disabled={approving}
                />
              </View>
            )}
          </CardBody>
        </Card>
        {error ? <Text style={styles.error}>{error}</Text> : null}
      </ScrollView>
    );
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.heading}>Budgets · FY{year}</Text>
      {error ? <Text style={styles.error}>{error}</Text> : null}
      {budgets.length === 0 ? (
        <Text style={styles.muted}>No budgets for this year.</Text>
      ) : (
        budgets.map((b) => (
          <TouchableOpacity key={b.budgetId} onPress={() => openBudget(b)}>
            <Card>
              <CardBody>
                <View style={styles.cardRow}>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.title}>{b.title}</Text>
                    <Text style={styles.muted}>{b.scopeLevel} · FY{b.periodYear}</Text>
                  </View>
                  <Badge variant={b.status === "ACTIVE" ? "success" : "info"}>{b.status}</Badge>
                </View>
              </CardBody>
            </Card>
          </TouchableOpacity>
        ))
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16, gap: 12 },
  center: { flex: 1, alignItems: "center", justifyContent: "center", gap: 8 },
  heading: { fontSize: 18, fontWeight: "600", marginBottom: 4 },
  title: { fontSize: 15, fontWeight: "600" },
  muted: { color: "#6b7280", fontSize: 13 },
  link: { color: "#2563eb", marginBottom: 8 },
  row: { flexDirection: "row", justifyContent: "space-between", paddingVertical: 4 },
  cardRow: { flexDirection: "row", alignItems: "center", gap: 8 },
  badgeRow: { marginTop: 10 },
  driver: { marginTop: 8, color: colors.gray[700], fontSize: 12 },
  approve: { marginTop: 16 },
  error: { color: "#dc2626", marginTop: 8 },
});
