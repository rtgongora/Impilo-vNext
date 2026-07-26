/**
 * NhumeTrackingScreen — Citizen-facing delivery tracking.
 *
 * Lists the citizen's Nhume deliveries, with drill-down to a status timeline,
 * coarse-grained recent location updates, and an OTP confirmation field for
 * recipients who need to acknowledge receipt. Maps render via Ndila when the
 * native module is available; this screen always provides a textual fallback.
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView, RefreshControl, TextInput, TouchableOpacity, Alert } from "react-native";
import { Screen, Header, Card, CardHeader, CardBody, Button, Badge, LoadingSpinner, EmptyState, colors } from "@impilo/mobile-design-system";
import {
  listNhumeDeliveries,
  getNhumeTimeline,
  getNhumeTracking,
  confirmNhumeOtp,
  cancelNhumeDelivery,
  type NhumeCitizenDelivery,
  type NhumeTimelineEvent,
  type NhumeTrackingEvent,
} from "../services/nhumeService";

const STATUS_TONE: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  DELIVERED: "default",
  IN_TRANSIT: "secondary",
  PICKED_UP: "secondary",
  EN_ROUTE_TO_PICKUP: "secondary",
  ASSIGNED: "secondary",
  AWAITING_PICKUP: "outline",
  CANCELLED: "destructive",
  FAILED: "destructive",
  RETURNED: "outline",
};

export interface NhumeTrackingScreenProps {
  /** Optional back handler (when hosted inside HomeScreen / overlay). */
  onBack?: () => void;
}

export function NhumeTrackingScreen({ onBack }: NhumeTrackingScreenProps = {}) {
  const [deliveries, setDeliveries] = useState<NhumeCitizenDelivery[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [active, setActive] = useState<NhumeCitizenDelivery | null>(null);
  const [timeline, setTimeline] = useState<NhumeTimelineEvent[]>([]);
  const [tracking, setTracking] = useState<NhumeTrackingEvent[]>([]);
  const [otp, setOtp] = useState("");
  const [confirming, setConfirming] = useState(false);

  const load = useCallback(async () => {
    const rows = await listNhumeDeliveries();
    setDeliveries(rows);
    setLoading(false);
    setRefreshing(false);
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const openDetail = useCallback(async (d: NhumeCitizenDelivery) => {
    setActive(d);
    const [tl, tr] = await Promise.all([getNhumeTimeline(d.delivery_id), getNhumeTracking(d.delivery_id)]);
    setTimeline(tl);
    setTracking(tr);
  }, []);

  const onConfirm = useCallback(async () => {
    if (!active || !otp) return;
    setConfirming(true);
    const ok = await confirmNhumeOtp(active.delivery_id, otp);
    setConfirming(false);
    if (ok) {
      setOtp("");
      Alert.alert("Delivery confirmed", "Thanks — your delivery is marked as received.");
      await load();
      await openDetail(active);
    } else {
      Alert.alert("Couldn't confirm", "Please check the OTP and try again, or contact support.");
    }
  }, [active, otp, load, openDetail]);

  const onCancel = useCallback(async () => {
    if (!active) return;
    Alert.alert("Cancel delivery?", "This may not be possible after dispatch.", [
      { text: "Keep delivery" },
      {
        text: "Cancel delivery",
        style: "destructive",
        onPress: async () => {
          const ok = await cancelNhumeDelivery(active.delivery_id, "citizen cancelled");
          if (ok) {
            await load();
            setActive(null);
          } else {
            Alert.alert("Couldn't cancel", "Please ask support to cancel for you.");
          }
        },
      },
    ]);
  }, [active, load]);

  if (loading) {
    return (
      <Screen>
        <Header title="Track delivery" onBack={onBack} />
        <LoadingSpinner size="md" />
      </Screen>
    );
  }

  if (active) {
    return (
      <Screen>
        <Header
          title={active.reference}
          subtitle={`${active.origin_label ?? "Origin"} → ${active.destination_label ?? "Destination"}`}
          onBack={() => setActive(null)}
        />
        <ScrollView style={{ flex: 1 }} contentContainerStyle={{ padding: 16, gap: 12 }}>
          <Card>
            <CardHeader title="Status" />
            <CardBody>
              <View style={styles.row}>
                <Badge variant={STATUS_TONE[active.status] ?? "outline"}>{active.status}</Badge>
                {active.sla_due_at ? (
                  <Text style={styles.muted}>Expected by {new Date(active.sla_due_at).toLocaleString()}</Text>
                ) : null}
              </View>
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Status timeline" />
            <CardBody>
              {timeline.length === 0 ? (
                <Text style={styles.muted}>Awaiting status updates.</Text>
              ) : (
                timeline.slice(-12).reverse().map((ev, idx) => (
                  <View key={idx} style={styles.timelineRow}>
                    <Badge variant={STATUS_TONE[ev.status] ?? "outline"}>{ev.status}</Badge>
                    <Text style={styles.muted}>
                      {ev.recorded_at ? new Date(ev.recorded_at).toLocaleString() : ""}
                    </Text>
                  </View>
                ))
              )}
            </CardBody>
          </Card>

          {tracking.length > 0 && (
            <Card>
              <CardHeader title="Recent location updates" />
              <CardBody>
                {tracking.slice(-8).reverse().map((pt, idx) => (
                  <Text key={idx} style={styles.muted}>
                    approx. {pt.latitude}, {pt.longitude} ·{" "}
                    {pt.recorded_at ? new Date(pt.recorded_at).toLocaleTimeString() : ""}
                  </Text>
                ))}
              </CardBody>
            </Card>
          )}

          <Card>
            <CardHeader title="Confirm delivery (OTP)" />
            <CardBody>
              <Text style={styles.muted}>If your courier asked for an OTP code, enter it here.</Text>
              <TextInput
                value={otp}
                onChangeText={setOtp}
                placeholder="e.g. 1234"
                keyboardType="number-pad"
                style={styles.input}
              />
              <Button
                onPress={onConfirm}
                disabled={!otp || confirming}
                title={confirming ? "Confirming…" : "Confirm receipt"}
              />
            </CardBody>
          </Card>

          <TouchableOpacity onPress={onCancel} style={styles.cancelLink}>
            <Text style={styles.cancelText}>Cancel delivery</Text>
          </TouchableOpacity>
        </ScrollView>
      </Screen>
    );
  }

  return (
    <Screen>
      <Header
        title="Track delivery"
        subtitle="Your active and recent Nhume deliveries"
        onBack={onBack}
      />
      <ScrollView
        style={{ flex: 1 }}
        contentContainerStyle={{ padding: 16, gap: 12 }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); void load(); }} />}
      >
        {deliveries.length === 0 ? (
          <EmptyState
            title="No deliveries yet"
            message="When you request a medicine, sample pickup or marketplace delivery, it shows up here."
          />
        ) : (
          deliveries.map((d) => (
            <TouchableOpacity key={d.delivery_id} onPress={() => openDetail(d)}>
              <Card>
                <CardBody>
                  <View style={styles.row}>
                    <Text style={styles.title}>{d.reference}</Text>
                    <Badge variant={STATUS_TONE[d.status] ?? "outline"}>{d.status}</Badge>
                  </View>
                  <Text style={styles.muted}>{d.origin_label ?? "Origin"} → {d.destination_label ?? "Destination"}</Text>
                  {d.sla_due_at ? (
                    <Text style={styles.muted}>by {new Date(d.sla_due_at).toLocaleString()}</Text>
                  ) : null}
                </CardBody>
              </Card>
            </TouchableOpacity>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  title: { fontSize: 16, fontWeight: "600", color: "#0F172A" },
  muted: { fontSize: 12, color: "#64748B", marginTop: 4 },
  timelineRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginVertical: 4 },
  input: {
    borderWidth: 1,
    borderColor: "#CBD5E1",
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
    marginVertical: 8,
    fontSize: 16,
  },
  cancelLink: { alignItems: "center", paddingVertical: 12 },
  cancelText: { color: colors.ui.error.main, fontSize: 14, fontWeight: "500" },
});
