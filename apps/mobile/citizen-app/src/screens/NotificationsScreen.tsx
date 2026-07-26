/**
 * NotificationsScreen — In-app notification feed for citizens.
 */

import React, { useEffect } from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import {
  useNotifications,
  type Notification,
} from "@impilo/mobile-messaging";
import { Screen, Header, Card, CardBody, Button, Badge, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
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
    return (
      <Screen>
        <Header title="Notifications" />
        <LoadingSpinner size="md" />
      </Screen>
    );
  }

  if (error) {
    return (
      <Screen>
        <Header title="Notifications" />
        <ErrorState
          title="Failed to load notifications"
          message={error.message}
          onRetry={refresh}
        />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="Notifications" />
      <View testID="notifications-screen" style={styles.container}>
        {notifications.length > 0 ? (
          <View style={styles.markAllReadRow}>
            <Button
              title="Mark all read"
              variant="ghost"
              size="sm"
              onPress={markAllRead}
              testID="mark-all-read"
            />
          </View>
        ) : null}
        {notifications.length === 0 ? (
          <EmptyState
            title="No notifications"
            message="You're all caught up"
          />
        ) : (
          notifications.map((n: Notification) => (
            <Card key={n.id}>
              <CardBody>
                <Pressable
                  onPress={() => !n.read && markRead(n.id)}
                  testID={`notification-${n.id}`}
                  style={[
                    styles.notificationRow,
                    { opacity: n.read ? 0.6 : 1 },
                  ]}
                >
                  <View style={styles.notificationContent}>
                    <View style={styles.titleRow}>
                      <Text style={styles.notificationTitle}>{n.title}</Text>
                      <Badge
                        variant={(PRIORITY_COLORS[n.priority] ?? "secondary") as "destructive" | "secondary" | "outline"}
                      >
                        {n.priority}
                      </Badge>
                    </View>
                    <Text style={styles.notificationBody}>{n.body}</Text>
                    <Text style={styles.notificationTimestamp}>
                      {formatTimestamp(n.createdAt)}
                    </Text>
                  </View>
                  {!n.read ? (
                    <View
                      style={styles.unreadDot}
                      accessibilityLabel="Unread"
                    />
                  ) : null}
                </Pressable>
              </CardBody>
            </Card>
          ))
        )}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  markAllReadRow: {
    marginBottom: 12,
    alignItems: "flex-end",
  },
  notificationRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
  },
  notificationContent: {
    flex: 1,
  },
  titleRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
  },
  notificationTitle: {
    fontWeight: "700",
  },
  notificationBody: {
    fontSize: 14,
    color: colors.gray[500],
    marginVertical: 4,
  },
  notificationTimestamp: {
    fontSize: 12,
    color: colors.gray[400],
  },
  unreadDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: "#3B82F6",
    marginLeft: 8,
    marginTop: 4,
  },
});
