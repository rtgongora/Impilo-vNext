import React from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { Card, CardBody } from "@impilo/mobile-design-system";

/**
 * PrivacyPolicyScreen — Tier-3 wave 2 placeholder for legal surface.
 * Maps to web: /privacy
 */
export function PrivacyPolicyScreen() {
  return (
    <ScrollView testID="privacy-policy-screen" contentContainerStyle={styles.container}>
      <Card>
        <CardBody>
          <Text style={styles.title}>Privacy Policy</Text>
          <Text style={styles.body}>
            This is a placeholder privacy policy surface for mobile parity. Content will be sourced from the canonical legal copy.
          </Text>
        </CardBody>
      </Card>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16, gap: 12 },
  title: { fontSize: 16, fontWeight: "800", color: "#111827" },
  body: { fontSize: 13, color: "#374151", marginTop: 10, lineHeight: 18 },
});

