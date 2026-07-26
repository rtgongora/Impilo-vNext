import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet, RefreshControl } from "react-native";
import { Screen, Header, Button, Badge, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import { fetchBloodOrders, submitBloodOrder, type BloodOrder } from "../../services/madiService";

export function MadiOrdersScreen() {
  const [orders, setOrders] = useState<BloodOrder[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [actingId, setActingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      setOrders(await fetchBloodOrders());
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function handleSubmit(orderId: string) {
    setActingId(orderId);
    await submitBloodOrder(orderId);
    await load();
    setActingId(null);
  }

  if (loading) return <Screen><Header title="Blood Orders" /><LoadingSpinner label="Loading orders..." /></Screen>;
  if (error) return <Screen><Header title="Blood Orders" /><ErrorState message={error.message} onRetry={load} /></Screen>;

  return (
    <Screen>
      <Header title="Blood Orders (MADI)" />
      <ScrollView
        style={styles.container}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(); }} />}
      >
        {orders.length === 0 ? (
          <EmptyState icon="water-outline" title="No blood orders" description="Orders for your facility context will appear here." />
        ) : (
          orders.map((order) => (
            <View key={order.order_id} style={styles.card} testID={`madi-order-${order.order_id}`}>
              <Text style={styles.title}>{order.component_type ?? "Blood component"}</Text>
              <Text style={styles.meta}>
                {order.blood_group ?? "—"} · {order.units_requested ?? 1} unit(s)
              </Text>
              <Text style={styles.meta}>Patient CPID: {order.patient_cpid?.slice(0, 12) ?? "—"}…</Text>
              <View style={styles.row}>
                <Badge variant="outline">{order.status ?? "DRAFT"}</Badge>
                {order.status === "DRAFT" ? (
                  <Button title={actingId === order.order_id ? "Submitting…" : "Submit"} size="sm" onPress={() => handleSubmit(order.order_id)} disabled={actingId === order.order_id} />
                ) : null}
              </View>
            </View>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  card: { backgroundColor: "#FFFFFF", borderRadius: 12, padding: 14, marginBottom: 12, borderWidth: 1, borderColor: colors.gray[200], gap: 4 },
  title: { fontSize: 15, fontWeight: "600" },
  meta: { fontSize: 12, color: colors.gray[500] },
  row: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginTop: 8 },
});
