/**
 * IdRecoverySection — Recover lost Health ID.
 * Maps to web: /citizen/id-recovery
 * Priority: MEDIUM
 */

import React from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { Screen, Header, Card, CardBody, Button, EmptyState } from "@impilo/mobile-design-system";

export function IdRecoverySection() {
  return (
    <Screen>
      <Header title="Recover Health ID" />
      <ScrollView style={styles.content} contentContainerStyle={styles.contentContainer}>
        <EmptyState
          title="ID Recovery"
          description="Recover your Health ID using your personal details or previous records."
          actionLabel="Start Recovery"
          onAction={() => {}}
        />
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { flex: 1 },
  contentContainer: { padding: 16, gap: 16 },
});