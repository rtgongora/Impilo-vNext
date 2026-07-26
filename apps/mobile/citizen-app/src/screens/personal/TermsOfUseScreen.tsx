import React from "react";
import { Text, StyleSheet, ScrollView } from "react-native";
import { Card, CardBody, colors } from "@impilo/mobile-design-system";

/**
 * TermsOfUseScreen — Tier-3 wave 2 placeholder for legal surface.
 * Maps to web: /terms
 */
export function TermsOfUseScreen() {
  return (
    <ScrollView testID="terms-of-use-screen" contentContainerStyle={styles.container}>
      <Card>
        <CardBody>
          <Text style={styles.title}>Terms of Use</Text>
          <Text style={styles.body}>
            This is a placeholder terms-of-use surface for mobile parity. Content will be sourced from the canonical legal copy.
          </Text>
        </CardBody>
      </Card>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16, gap: 12 },
  title: { fontSize: 16, fontWeight: "800", color: colors.gray[900] },
  body: { fontSize: 13, color: colors.gray[700], marginTop: 10, lineHeight: 18 },
});

