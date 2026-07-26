/**
 * ActivityFeedScreen — Unified work activity feed for the provider.
 *
 * Uses @impilo/mobile-timeline to display a chronological feed of events.
 */

import React, { useCallback } from "react";
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from "react-native";
import { Screen, Header, Card, CardBody, Badge, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import { useMyTimeline, type TimelineEvent } from "@impilo/mobile-timeline";

const EVENT_TYPE_LABELS: Record<string, string> = {
  ENCOUNTER_CREATED: "Visit Started",
  ENCOUNTER_CLOSED: "Visit Closed",
  VITAL_RECORDED: "Vital Recorded",
  DIAGNOSIS_ADDED: "Diagnosis Added",
  PRESCRIPTION_CREATED: "Prescription Created",
  LAB_ORDERED: "Lab Ordered",
  LAB_RESULT: "Lab Result",
  REFERRAL_CREATED: "Referral Created",
  TASK_ASSIGNED: "Task Assigned",
  TASK_COMPLETED: "Task Completed",
  MESSAGE_RECEIVED: "Message Received",
};

function formatTime(ts: string): string {
  return new Date(ts).toLocaleString();
}

export function ActivityFeedScreen() {
  const { events, loading, error, loadMore, hasMore, refresh } = useMyTimeline();

  if (loading && events.length === 0) {
    return (
      <Screen>
        <Header title="Activity" />
        <LoadingSpinner size="md" />
      </Screen>
    );
  }

  if (error) {
    return (
      <Screen>
        <Header title="Activity" />
        <ErrorState title="Error" message={error.message} onRetry={refresh} />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="Activity Feed" />
      <ScrollView testID="activity-feed" style={styles.container}>
        {events.length === 0 ? (
          <EmptyState title="No activity" message="Your work activity will appear here" />
        ) : (
          events.map((event: TimelineEvent) => (
            <Card key={event.id}>
              <CardBody>
                <View testID={`event-${event.id}`}>
                  <View style={styles.eventHeader}>
                    <Badge variant="secondary">
                      {EVENT_TYPE_LABELS[event.type] ?? event.type}
                    </Badge>
                    <Text style={styles.timestamp}>{formatTime(event.occurredAt ?? event.timestamp)}</Text>
                  </View>
                  <Text style={styles.summary}>{event.summary}</Text>
                  {event.subjectName ? (
                    <Text style={styles.subjectName}>{event.subjectName}</Text>
                  ) : null}
                </View>
              </CardBody>
            </Card>
          ))
        )}
        {hasMore ? (
          <View style={styles.loadMoreContainer}>
            <TouchableOpacity
              onPress={loadMore}
              testID="load-more"
              style={styles.loadMoreButton}
            >
              <Text>{loading ? "Loading..." : "Load More"}</Text>
            </TouchableOpacity>
          </View>
        ) : null}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  eventHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  timestamp: {
    fontSize: 12,
    color: colors.gray[400],
  },
  summary: {
    marginTop: 4,
    fontSize: 14,
  },
  subjectName: {
    fontSize: 12,
    color: colors.gray[500],
  },
  loadMoreContainer: {
    alignItems: "center",
    marginTop: 16,
  },
  loadMoreButton: {
    paddingVertical: 8,
    paddingHorizontal: 16,
  },
});
