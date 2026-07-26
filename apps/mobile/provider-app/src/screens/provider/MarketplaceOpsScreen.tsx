/**
 * MarketplaceOpsScreen — Tier-2 provider marketplace ops surface.
 */
import React from "react";
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from "react-native";
import { Screen, Header, Card, CardBody, Badge, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import { useAppStore } from "../../stores/appStore";
import { deliveryAction, listAssignedDeliveries, type ProviderDelivery } from "../../services/deliveryService";

export function MarketplaceOpsScreen() {
  const { isOnline, setGlobalError } = useAppStore();
  const [loading, setLoading] = React.useState(true);
  const [deliveries, setDeliveries] = React.useState<ProviderDelivery[]>([]);

  const load = React.useCallback(async () => {
    setLoading(true);
    try {
      setDeliveries(await listAssignedDeliveries());
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    void load();
  }, [load]);

  const runAction = React.useCallback(async (id: string, action: Parameters<typeof deliveryAction>[1]) => {
    if (!isOnline) {
      setGlobalError({
        code: "DELIVERY_SYNC_REQUIRED",
        message: "You are offline. Delivery commands require sync when connection resumes.",
      });
      return;
    }
    await deliveryAction(id, action, action === "proof" ? { proofType: "OTP", otp: "123456" } : undefined);
    await load();
  }, [isOnline, load, setGlobalError]);

  return (
    <Screen>
      <Header title="Marketplace Ops" />
      <ScrollView testID="marketplace-ops-screen" contentContainerStyle={styles.content}>
        <Card>
          <CardBody>
            <Text style={styles.title}>Marketplace Operations</Text>
            <Text style={styles.sub}>
              Tier-2 parity surface for vendor fulfilment, orders, pickup handoff, and substitutions.
            </Text>
            <View style={styles.badges}>
              <Badge variant="secondary">Vendors</Badge>
              <Badge variant="outline">Vendor orders</Badge>
              <Badge variant="outline">Pickup</Badge>
              <Badge variant="outline">Substitutions</Badge>
            </View>
            {!isOnline ? <Text style={styles.offline}>Offline: actions will require sync.</Text> : null}
          </CardBody>
        </Card>
        {loading ? (
          <View style={styles.loading}>
            <LoadingSpinner size="md" />
          </View>
        ) : deliveries.length === 0 ? (
          <Text style={styles.sub}>No assigned deliveries yet.</Text>
        ) : (
          deliveries.map((delivery) => (
            <Card key={delivery.id}>
              <CardBody>
                <Text style={styles.title}>
                  {delivery.pickupLocation} {"->"} {delivery.dropoffLocation}
                </Text>
                <View style={styles.row}>
                  <Badge variant="outline">{delivery.status}</Badge>
                  <TouchableOpacity style={styles.action} onPress={() => runAction(delivery.id, "accept")}><Text style={styles.actionText}>Accept</Text></TouchableOpacity>
                  <TouchableOpacity style={styles.action} onPress={() => runAction(delivery.id, "pickup")}><Text style={styles.actionText}>Pickup</Text></TouchableOpacity>
                  <TouchableOpacity style={styles.action} onPress={() => runAction(delivery.id, "start")}><Text style={styles.actionText}>Start</Text></TouchableOpacity>
                  <TouchableOpacity style={styles.action} onPress={() => runAction(delivery.id, "proof")}><Text style={styles.actionText}>Proof</Text></TouchableOpacity>
                </View>
              </CardBody>
            </Card>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { padding: 16, gap: 12 },
  title: { fontSize: 16, fontWeight: "700", color: colors.gray[900] },
  sub: { fontSize: 13, color: colors.gray[500], marginTop: 6 },
  badges: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginTop: 10 },
  row: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginTop: 10, alignItems: "center" },
  action: { paddingVertical: 6, paddingHorizontal: 10, borderRadius: 10, backgroundColor: colors.gray[200] },
  actionText: { fontSize: 12, fontWeight: "600", color: colors.gray[900] },
  offline: { marginTop: 8, fontSize: 12, color: "#B45309" },
  loading: { alignItems: "center", paddingVertical: 12 },
});

