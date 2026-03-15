/**
 * ActivityFeedScreen — Unified work activity feed for the provider.
 *
 * Uses @impilo/mobile-timeline to display a chronological feed of events.
 */

import React, { useCallback } from "react";
import { Screen, Header, Card, CardBody, Badge, LoadingSpinner, EmptyState, ErrorState } from "@impilo/mobile-design-system";
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
    return React.createElement(Screen, null,
      React.createElement(Header, { title: "Activity" }),
      React.createElement(LoadingSpinner, { size: "md" })
    );
  }

  if (error) {
    return React.createElement(Screen, null,
      React.createElement(Header, { title: "Activity" }),
      React.createElement(ErrorState, { title: "Error", message: error, onRetry: refresh })
    );
  }

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, { title: "Activity Feed" }),
    React.createElement(
      "div",
      { "data-testid": "activity-feed", style: { padding: "16px" } },
      events.length === 0
        ? React.createElement(EmptyState, { title: "No activity", message: "Your work activity will appear here" })
        : events.map((event: TimelineEvent) =>
            React.createElement(
              Card,
              { key: event.id },
              React.createElement(
                CardBody,
                null,
                React.createElement(
                  "div",
                  { "data-testid": `event-${event.id}` },
                  React.createElement(
                    "div",
                    { style: { display: "flex", justifyContent: "space-between", alignItems: "center" } },
                    React.createElement(Badge, { variant: "secondary", children: EVENT_TYPE_LABELS[event.type] ?? event.type }),
                    React.createElement("span", { style: { fontSize: "12px", color: "#9CA3AF" } }, formatTime(event.occurredAt))
                  ),
                  React.createElement("p", { style: { marginTop: "4px", fontSize: "14px" } }, event.summary),
                  event.subjectName
                    ? React.createElement("p", { style: { fontSize: "12px", color: "#6B7280" } }, event.subjectName)
                    : null
                )
              )
            )
          ),
      hasMore
        ? React.createElement(
            "div",
            { style: { textAlign: "center", marginTop: "16px" } },
            React.createElement("button", {
              onClick: loadMore,
              "data-testid": "load-more",
              style: { padding: "8px 16px", cursor: "pointer" },
            }, loading ? "Loading..." : "Load More")
          )
        : null
    )
  );
}
