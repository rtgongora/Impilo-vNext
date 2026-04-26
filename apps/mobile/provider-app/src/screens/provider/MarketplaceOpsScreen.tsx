/**
 * MarketplaceOpsScreen — Tier-2 provider marketplace ops surface.
 */
import React from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { Screen, Header, Card, CardBody, Badge } from "@impilo/mobile-design-system";

export function MarketplaceOpsScreen() {
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
          </CardBody>
        </Card>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { padding: 16, gap: 12 },
  title: { fontSize: 16, fontWeight: "700", color: "#111827" },
  sub: { fontSize: 13, color: "#6B7280", marginTop: 6 },
  badges: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginTop: 10 },
});

