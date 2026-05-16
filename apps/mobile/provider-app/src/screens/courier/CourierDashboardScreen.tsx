/**
 * CourierDashboardScreen — landing screen for Nhume Courier mode.
 *
 * Shows the courier's assigned deliveries grouped by lifecycle stage with one
 * tap to progress each one. Backed by the Nhume backend through the provider
 * BFF (`/internal/v1/mobile/provider/nhume/...`).
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView, RefreshControl, TouchableOpacity, Alert } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardHeader,
  CardBody,
  Button,
  Badge,
  LoadingSpinner,
  EmptyState,
} from "@impilo/mobile-design-system";
import {
  listAssignedDeliveries,
  acceptDelivery,
  declineDelivery,
  startPickup,
  startTransit,
  type NhumeCourierDelivery,
} from "../../services/nhumeService";

const STAGE_ORDER: string[] = ["ASSIGNED", "ACCEPTED", "EN_ROUTE_TO_PICKUP", "PICKED_UP", "IN_TRANSIT", "AT_DESTINATION"];

const STATUS_TONE: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  ASSIGNED: "secondary",
  ACCEPTED: "secondary",
  EN_ROUTE_TO_PICKUP: "secondary",
  PICKED_UP: "secondary",
  IN_TRANSIT: "secondary",
  AT_DESTINATION: "default",
  DELIVERED: "default",
  FAILED: "destructive",
};

export function CourierDashboardScreen() {
  const [rows, setRows] = useState<NhumeCourierDelivery[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    const list = await listAssignedDeliveries();
    setRows(list);
    setLoading(false);
    setRefreshing(false);
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const onAccept = useCallback(async (id: string) => {
    const ok = await acceptDelivery(id);
    if (!ok) Alert.alert("Couldn't accept", "Please retry. If it keeps failing, contact dispatch.");
    await load();
  }, [load]);

  const onDecline = useCallback(async (id: string) => {
    Alert.prompt?.(
      "Decline delivery",
      "Reason (visible to dispatcher):",
      async (reason) => {
        const ok = await declineDelivery(id, reason ?? "courier declined");
        if (!ok) Alert.alert("Couldn't decline", "Please retry.");
        await load();
      },
    );
  }, [load]);

  const onPickup = useCallback(async (id: string) => {
    const ok = await startPickup(id);
    if (!ok) Alert.alert("Couldn't start pickup", "Please retry or check connection.");
    await load();
  }, [load]);

  const onTransit = useCallback(async (id: string) => {
    const ok = await startTransit(id);
    if (!ok) Alert.alert("Couldn't start transit", "Please retry or check connection.");
    await load();
  }, [load]);

  if (loading) {
    return (
      <Screen>
        <Header title="Courier mode (Nhume)" />
        <LoadingSpinner size="md" />
      </Screen>
    );
  }

  const grouped: Record<string, NhumeCourierDelivery[]> = {};
  for (const r of rows) {
    const key = STAGE_ORDER.includes(r.status) ? r.status : "OTHER";
    grouped[key] = grouped[key] ?? [];
    grouped[key].push(r);
  }

  return (
    <Screen>
      <Header title="Courier mode (Nhume)" subtitle="Your assigned deliveries" />
      <ScrollView
        style={{ flex: 1 }}
        contentContainerStyle={{ padding: 16, gap: 12 }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); void load(); }} />}
      >
        {rows.length === 0 ? (
          <EmptyState
            title="No assignments"
            message="Your dispatcher hasn't assigned any deliveries to you yet. Pull to refresh."
          />
        ) : (
          STAGE_ORDER.map((stage) => {
            const items = grouped[stage] ?? [];
            if (items.length === 0) return null;
            return (
              <Card key={stage}>
                <CardHeader title={stage.replace(/_/g, " ")} />
                <CardBody>
                  {items.map((d) => (
                    <View key={d.delivery_id} style={styles.deliveryRow}>
                      <View style={styles.deliveryHeader}>
                        <Text style={styles.deliveryRef}>{d.reference}</Text>
                        <Badge variant={STATUS_TONE[d.status] ?? "outline"}>{d.status}</Badge>
                      </View>
                      <Text style={styles.muted}>
                        {d.origin_label ?? "Origin"} → {d.destination_label ?? "Destination"}
                      </Text>
                      <View style={styles.tagRow}>
                        {d.cold_chain_required ? <Badge variant="secondary">cold-chain</Badge> : null}
                        {d.chain_of_custody_required ? <Badge variant="secondary">custody</Badge> : null}
                        {d.priority && d.priority !== "STANDARD" ? <Badge variant="destructive">{d.priority}</Badge> : null}
                      </View>
                      <View style={styles.actionRow}>
                        {stage === "ASSIGNED" && (
                          <>
                            <Button size="sm" variant="primary" onPress={() => onAccept(d.delivery_id)} title="Accept" />
                            <Button size="sm" variant="outline" onPress={() => onDecline(d.delivery_id)} title="Decline" />
                          </>
                        )}
                        {stage === "ACCEPTED" && (
                          <Button size="sm" variant="primary" onPress={() => onPickup(d.delivery_id)} title="Start pickup" />
                        )}
                        {stage === "PICKED_UP" && (
                          <Button size="sm" variant="primary" onPress={() => onTransit(d.delivery_id)} title="Start transit" />
                        )}
                      </View>
                    </View>
                  ))}
                </CardBody>
              </Card>
            );
          })
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  deliveryRow: { paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: "#F1F5F9" },
  deliveryHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginBottom: 4 },
  deliveryRef: { fontSize: 16, fontWeight: "600", color: "#0F172A" },
  muted: { fontSize: 12, color: "#64748B", marginTop: 2 },
  tagRow: { flexDirection: "row", gap: 6, marginTop: 6, flexWrap: "wrap" },
  actionRow: { flexDirection: "row", gap: 8, marginTop: 8 },
});
