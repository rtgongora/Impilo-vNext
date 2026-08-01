/**
 * HealthTimelineScreen — Unified vertical health timeline.
 */

import React from "react";
import { View, Text, StyleSheet, ScrollView, RefreshControl, TouchableOpacity } from "react-native";
import { LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import { useMyTimeline, type TimelineEvent, type TimelineEventType } from "@impilo/mobile-timeline";

const TYPE_ICON: Partial<Record<TimelineEventType, string>> = {
  VISIT: "\u{1F3E5}",
  LAB_RESULT: "\u{1F9EA}",
  PRESCRIPTION: "\u{1F48A}",
  APPOINTMENT: "\u{1F4C5}",
  IMMUNIZATION: "\u{1F489}",
};

const TYPE_DOT_COLOR: Partial<Record<TimelineEventType, string>> = {
  VISIT: "#3B82F6",
  LAB_RESULT: "#8B5CF6",
  PRESCRIPTION: "#10B981",
  APPOINTMENT: colors.ui.warning.main,
  IMMUNIZATION: "#EF4444",
};

export function HealthTimelineScreen() {
  const { events, isLoading, error, refresh, loadMore, hasMore } = useMyTimeline(undefined, { pageSize: 40 });

  if (isLoading) return <LoadingSpinner size="md" />;
  if (error) return <ErrorState title="Timeline unavailable" message={error.message} onRetry={refresh} />;

  if (events.length === 0) {
    return (
      <EmptyState
        title="Your timeline is ready"
        message="Verified care events will appear here as they are recorded. An empty timeline does not invent history."
      />
    );
  }

  return (
    <ScrollView
      testID="health-timeline-screen"
      style={styles.scrollView}
      showsVerticalScrollIndicator={false}
      refreshControl={<RefreshControl refreshing={isLoading} onRefresh={refresh} />}
    >
      <View style={styles.container}>
        <Text style={styles.heading}>Health Timeline</Text>

        <View style={styles.timeline}>
          {events.map((entry: TimelineEvent, index) => {
            const dotColor = TYPE_DOT_COLOR[entry.type] ?? colors.gray[400];
            const icon = TYPE_ICON[entry.type] ?? "\u{1F4CB}";
            const isLast = index === events.length - 1;

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
                      {new Date(entry.timestamp).toLocaleDateString()}
                    </Text>
                  </View>
                  <Text style={styles.entryTitle}>{entry.title}</Text>
                  {entry.summary ? (
                    <Text style={styles.entryDescription}>{entry.summary}</Text>
                  ) : null}
                  {entry.facilityName ? (
                    <Text style={styles.entryFacility}>{entry.facilityName}</Text>
                  ) : null}
                  {entry.actorName ? (
                    <Text style={styles.entryProvider}>{entry.actorName}</Text>
                  ) : null}
                  <Text style={styles.provenance}>Source: {entry.provenance.system}</Text>
                  {entry.actions.map((action) => (
                    <TouchableOpacity
                      key={action.id}
                      disabled={!action.enabled}
                      accessibilityRole="button"
                      accessibilityHint={action.unavailableReason}
                      style={styles.action}
                    >
                      <Text style={styles.actionText}>{action.label}</Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </View>
            );
          })}
        </View>
        {hasMore ? (
          <TouchableOpacity testID="timeline-load-more" onPress={loadMore} style={styles.loadMore}>
            <Text style={styles.loadMoreText}>{isLoading ? "Loading…" : "Show earlier events"}</Text>
          </TouchableOpacity>
        ) : null}
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
    backgroundColor: colors.gray[200],
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
    color: colors.gray[500],
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },
  entryDate: {
    fontSize: 12,
    color: colors.gray[400],
    marginLeft: "auto",
  },
  entryTitle: {
    fontSize: 15,
    fontWeight: "700",
    color: colors.gray[900],
    marginBottom: 2,
  },
  entryDescription: {
    fontSize: 13,
    color: colors.gray[700],
    marginVertical: 2,
  },
  entryFacility: {
    fontSize: 13,
    color: colors.gray[500],
    marginVertical: 2,
  },
  entryProvider: {
    fontSize: 13,
    color: colors.gray[400],
    marginVertical: 2,
  },
  provenance: { fontSize: 11, color: colors.gray[400], marginTop: 4 },
  action: { alignSelf: "flex-start", marginTop: 8, minHeight: 44, justifyContent: "center" },
  actionText: { color: "#007A3D", fontWeight: "600" },
  loadMore: { minHeight: 48, alignItems: "center", justifyContent: "center" },
  loadMoreText: { color: "#007A3D", fontWeight: "600" },
});
