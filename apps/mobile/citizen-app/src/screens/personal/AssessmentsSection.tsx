/**
 * AssessmentsSection — Patient-reported assessments.
 * Maps to web: /ehr/[patientId]/assessments
 * Priority: MEDIUM
 */

import React from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { Screen, Header, Button, EmptyState } from "@impilo/mobile-design-system";

export function AssessmentsSection() {
  return (
    <Screen>
      <Header title="Assessments" />
      <ScrollView style={styles.content} contentContainerStyle={styles.contentContainer}>
        <EmptyState
          title="No assessments"
          description="Complete health assessments to help your care team understand your progress."
          actionLabel="Start Assessment"
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