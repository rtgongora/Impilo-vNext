/**
 * HealthTimelineScreen — Unified vertical health timeline.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import {
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { getTimeline } from "../../services/healthTimelineService";
import type { TimelineEntry, TimelineEntryType } from "../../types";

const TYPE_ICON: Record<TimelineEntryType, string> = {
  ENCOUNTER: "\u{1F3E5}",
  LAB_RESULT: "\u{1F9EA}",
  PRESCRIPTION: "\u{1F48A}",
  APPOINTMENT: "\u{1F4C5}",
  IMMUNIZATION: "\u{1F489}",
};

const TYPE_DOT_COLOR: Record<TimelineEntryType, string> = {
  ENCOUNTER: "#3B82F6",
  LAB_RESULT: "#8B5CF6",
  PRESCRIPTION: "#10B981",
  APPOINTMENT: "#F59E0B",
  IMMUNIZATION: "#EF4444",
};

export function HealthTimelineScreen() {
  const [entries, setEntries] = useState<TimelineEntry[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await getTimeline({ size: 100 });
      setEntries(result.items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (isLoading) return <LoadingSpinner size="md" />;
  if (error) return <ErrorState title="Error" message={error.message} onRetry={load} />;

  if (entries.length === 0) {
    return (
      <EmptyState
        title="No timeline entries"
        message="Your health history will appear here as you receive care"
      />
    );
  }

  return (
    <ScrollView testID="health-timeline-screen" style={styles.scrollView} showsVerticalScrollIndicator={false}>
      <View style={styles.container}>
        <Text style={styles.heading}>Health Timeline</Text>

        <View style={styles.timeline}>
          {entries.map((entry, index) => {
            const dotColor = TYPE_DOT_COLOR[entry.type] ?? "#9CA3AF";
            const icon = TYPE_ICON[entry.type] ?? "\u{1F4CB}";
            const isLast = index === entries.length - 1;

            return (
              <View key={entry.id} testID={`timeline-entry-${entry.id}`} style={styles.entryRow}>
                {/* Timeline rail */}
                <View style={styles.rail}>
                  <View style={[styles.dot, { backgroundColor: dotColor }]} />
                  {!isLast ? <View style={styles.connector} /> : null}
                </View>

                {/* Content */}
                <View style={styles.entryContent}>
                  <View style={styles.entryHeader}>
                    <Text style={styles.typeIcon}>{icon}</Text>
                    <Text style={styles.entryType}>
                      {entry.type.replace(/_/g, " ")}
                    </Text>
                    <Text style={styles.entryDate}>
                      {new Date(entry.date).toLocaleDateString()}
                    </Text>
                  </View>
                  <Text style={styles.entryTitle}>{entry.title}</Text>
                  {entry.description ? (
                    <Text style={styles.entryDescription}>{entry.description}</Text>
                  ) : null}
                  {entry.facilityName ? (
                    <Text style={styles.entryFacility}>{entry.facilityName}</Text>
                  ) : null}
                  {entry.provider ? (
                    <Text style={styles.entryProvider}>{entry.provider}</Text>
                  ) : null}
                </View>
              </View>
            );
          })}
        </View>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
  },
  container: {
    gap: 16,
    paddingBottom: 24,
  },
  heading: {
    fontSize: 18,
    fontWeight: "600",
  },
  timeline: {
    gap: 0,
  },
  entryRow: {
    flexDirection: "row",
    minHeight: 80,
  },
  rail: {
    width: 32,
    alignItems: "center",
  },
  dot: {
    width: 14,
    height: 14,
    borderRadius: 7,
    marginTop: 4,
  },
  connector: {
    width: 2,
    flex: 1,
    backgroundColor: "#E5E7EB",
    marginVertical: 4,
  },
  entryContent: {
    flex: 1,
    paddingLeft: 12,
    paddingBottom: 20,
  },
  entryHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    marginBottom: 4,
  },
  typeIcon: {
    fontSize: 16,
  },
  entryType: {
    fontSize: 12,
    fontWeight: "600",
    color: "#6B7280",
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },
  entryDate: {
    fontSize: 12,
    color: "#9CA3AF",
    marginLeft: "auto",
  },
  entryTitle: {
    fontSize: 15,
    fontWeight: "700",
    color: "#111827",
    marginBottom: 2,
  },
  entryDescription: {
    fontSize: 13,
    color: "#374151",
    marginVertical: 2,
  },
  entryFacility: {
    fontSize: 13,
    color: "#6B7280",
    marginVertical: 2,
  },
  entryProvider: {
    fontSize: 13,
    color: "#9CA3AF",
    marginVertical: 2,
  },
});
