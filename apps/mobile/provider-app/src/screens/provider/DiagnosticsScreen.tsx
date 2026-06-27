/**
 * DiagnosticsScreen — provider diagnostic/imaging order tracking + results inbox (W9 mobile parity).
 *
 * Real data via the experience-bff diagnostics surface → OROS. Mirrors the web
 * /diagnostics/orders + results-inbox surfaces for the provider on mobile.
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView, Pressable } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { useSyncEngine } from "@impilo/mobile-offline";
import { fetchDiagnosticOrders, fetchResultsInbox, type DiagnosticOrder } from "../../services/diagnosticsService";

type ViewMode = "ORDERS" | "INBOX";

export function DiagnosticsScreen() {
  const [mode, setMode] = useState<ViewMode>("ORDERS");
  const [orders, setOrders] = useState<DiagnosticOrder[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { pendingCount } = useSyncEngine();

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = mode === "ORDERS"
        ? await fetchDiagnosticOrders({ type: "IMAGING" })
        : await fetchResultsInbox();
      setOrders(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load diagnostics");
    } finally {
      setLoading(false);
    }
  }, [mode]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <Screen>
      <Header title="Diagnostics" />
      {pendingCount > 0 ? (
        <Text testID="diag-pending-sync" style={styles.pending}>
          {pendingCount} change(s) queued offline — will sync when back online
        </Text>
      ) : null}
      <View style={styles.tabRow}>
        {(["ORDERS", "INBOX"] as ViewMode[]).map((m) => (
          <Pressable key={m} testID={`diag-tab-${m}`}
            style={[styles.tab, mode === m ? styles.tabActive : undefined]}
            onPress={() => setMode(m)}>
            <Text style={[styles.tabText, mode === m ? styles.tabTextActive : undefined]}>
              {m === "ORDERS" ? "Order tracking" : "Results inbox"}
            </Text>
          </Pressable>
        ))}
      </View>

      {loading ? (
        <LoadingSpinner size="lg" />
      ) : error && orders.length === 0 ? (
        <ErrorState title="Error" message={error} onRetry={load} />
      ) : orders.length === 0 ? (
        <EmptyState title="Nothing here"
          message={mode === "ORDERS" ? "No imaging orders in flight" : "No results awaiting review"} />
      ) : (
        <ScrollView testID="diagnostics-screen" style={styles.container}>
          {orders.map((o) => (
            <Card key={o.orderId}>
              <CardBody>
                <View style={styles.row}>
                  <Text style={styles.orderId}>{o.orderId}</Text>
                  <Text style={styles.state}>{o.imagingState ?? o.status}</Text>
                </View>
                <Text style={styles.meta}>
                  Patient {o.patientCpid}
                  {o.accessionNumber ? ` · ${o.accessionNumber}` : ""}
                </Text>
                {o.placedAt ? (
                  <Text style={styles.meta}>Placed {new Date(o.placedAt).toLocaleString()}</Text>
                ) : null}
              </CardBody>
            </Card>
          ))}
          <View style={styles.refresh}>
            <Button title="Refresh" variant="outline" onPress={load} testID="refresh-diagnostics" />
          </View>
        </ScrollView>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16 },
  tabRow: { flexDirection: "row", gap: 8, padding: 12 },
  tab: { paddingVertical: 6, paddingHorizontal: 12, borderRadius: 999, backgroundColor: "#eef2f7" },
  tabActive: { backgroundColor: "#1d4ed8" },
  tabText: { fontSize: 13, color: "#334155" },
  tabTextActive: { color: "#ffffff", fontWeight: "600" },
  row: { flexDirection: "row", justifyContent: "space-between", marginBottom: 4 },
  orderId: { fontSize: 13, fontWeight: "600", fontFamily: "monospace" },
  state: { fontSize: 12, color: "#1d4ed8", fontWeight: "600" },
  meta: { fontSize: 12, color: "#64748b" },
  refresh: { marginTop: 8, marginBottom: 24 },
  pending: { fontSize: 12, color: "#92400e", backgroundColor: "#fef3c7", paddingHorizontal: 12, paddingVertical: 6 },
});
