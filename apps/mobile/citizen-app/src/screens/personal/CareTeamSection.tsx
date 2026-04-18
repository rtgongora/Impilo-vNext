/**
 * CareTeamSection — View care team members.
 * Maps to web: /ehr/[patientId]/care-team
 * Priority: MEDIUM
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { Screen, Header, Card, CardBody, Button, Badge, LoadingSpinner, EmptyState, Avatar } from "@impilo/mobile-design-system";

export function CareTeamSection() {
  const [isLoading, setIsLoading] = useState(true);

  return (
    <Screen>
      <Header title="Care Team" />
      <ScrollView style={styles.content} contentContainerStyle={styles.contentContainer}>
        <EmptyState
          title="No care team"
          description="Your care team will appear here when assigned."
        />
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { flex: 1 },
  contentContainer: { padding: 16, gap: 16 },
});