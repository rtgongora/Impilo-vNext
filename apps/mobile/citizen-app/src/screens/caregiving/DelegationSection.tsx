/**
 * DelegationSection — Manage caregiving delegation.
 * Maps to web: /caregiving/delegation
 * Priority: MEDIUM
 */

import React from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { Screen, Header, Button, EmptyState } from "@impilo/mobile-design-system";

export function DelegationSection() {
  return (
    <Screen>
      <Header title="Delegation" />
      <ScrollView style={styles.content} contentContainerStyle={styles.contentContainer}>
        <EmptyState
          title="No delegations"
          description="Delegate access to your health records to caregivers or family members."
          actionLabel="Add Delegation"
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