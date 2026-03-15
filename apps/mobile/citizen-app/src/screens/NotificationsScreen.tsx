/**
 * NotificationsScreen — In-app notification feed for citizens.
 */

import React, { useEffect, useCallback } from "react";
import {
  useNotifications,
  type Notification,
} from "@impilo/mobile-messaging";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { appStore } from "../stores/appStore";

const PRIORITY_COLORS: Record<string, string> = {
  HIGH: "destructive",
  NORMAL: "secondary",
  LOW: "outline",
};

function formatTimestamp(ts: string): string {
  const date = new Date(ts);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  if (diffMins < 1) return "just now";
  if (diffMins < 60) return `${diffMins}m ago`;
  const diffHours = Math.floor(diffMins / 60);
  if (diffHours < 24) return `${diffHours}h ago`;
  return date.toLocaleDateString();
}

export function NotificationsScreen() {
  const { notifications, isLoading, error, refresh, markRead, markAllRead } = useNotifications();

  useEffect(() => {
    const unread = notifications.filter((n) => !n.read).length;
    appStore.getState().setUnreadNotifications(unread);
  }, [notifications]);

  if (isLoading) {
    return React.createElement(
      Screen,
      null,
      React.createElement(Header, { title: "Notifications" }),
      React.createElement(LoadingSpinner, { size: "md" })
    );
  }

  if (error) {
    return React.createElement(
      Screen,
      null,
      React.createElement(Header, { title: "Notifications" }),
      React.createElement(ErrorState, {
        title: "Failed to load notifications",
        message: error.message,
        onRetry: refresh,
      })
    );
  }

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, { title: "Notifications" }),
    React.createElement(
      "div",
      { "data-testid": "notifications-screen", style: { padding: "16px" } },
      notifications.length > 0
        ? React.createElement(
            "div",
            { style: { marginBottom: "12px", textAlign: "right" } },
            React.createElement(Button, {
              title: "Mark all read",
              variant: "ghost",
              size: "sm",
              onPress: markAllRead,
              testID: "mark-all-read",
            })
          )
        : null,
      notifications.length === 0
        ? React.createElement(EmptyState, {
            title: "No notifications",
            message: "You're all caught up",
          })
        : notifications.map((n: Notification) =>
            React.createElement(
              Card,
              { key: n.id },
              React.createElement(
                CardBody,
                null,
                React.createElement(
                  "div",
                  {
                    style: {
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "flex-start",
                      opacity: n.read ? 0.6 : 1,
                      cursor: n.read ? "default" : "pointer",
                    },
                    onClick: () => !n.read && markRead(n.id),
                    "data-testid": `notification-${n.id}`,
                  },
                  React.createElement(
                    "div",
                    { style: { flex: 1 } },
                    React.createElement(
                      "div",
                      { style: { display: "flex", gap: "8px", alignItems: "center" } },
                      React.createElement("strong", null, n.title),
                      React.createElement(Badge, {
                        variant: (PRIORITY_COLORS[n.priority] ?? "secondary") as "destructive" | "secondary" | "outline",
                        children: n.priority,
                      })
                    ),
                    React.createElement(
                      "p",
                      { style: { fontSize: "14px", color: "#6B7280", margin: "4px 0" } },
                      n.body
                    ),
                    React.createElement(
                      "span",
                      { style: { fontSize: "12px", color: "#9CA3AF" } },
                      formatTimestamp(n.createdAt)
                    )
                  ),
                  !n.read
                    ? React.createElement("div", {
                        style: {
                          width: "8px",
                          height: "8px",
                          borderRadius: "50%",
                          backgroundColor: "#3B82F6",
                          marginLeft: "8px",
                          marginTop: "4px",
                        },
                        "aria-label": "Unread",
                      })
                    : null
                )
              )
            )
          )
    )
  );
}
