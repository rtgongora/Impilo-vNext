/**
 * NotificationsScreen — In-app notification feed with push registration.
 *
 * Uses @impilo/mobile-messaging for notification fetching and real-time updates.
 */

import React, { useEffect, useCallback } from "react";
import { View, Text, StyleSheet, Platform } from "react-native";
import { Pressable } from "react-native";
import {
  useNotifications,
  usePushRegistration,
  markRead,
  markAllRead,
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
  URGENT: "destructive",
  HIGH: "warning",
  MEDIUM: "secondary",
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
  const { notifications, loading, error, refresh } = useNotifications();
  const { register } = usePushRegistration();

  useEffect(() => {
    register({ platform: Platform.OS, token: "push-token" }).catch(() => {
      // Push registration is best-effort; silent failure acceptable
    });
  }, [register]);

  useEffect(() => {
    const unread = notifications.filter((n) => !n.readAt).length;
    appStore.getState().setUnreadNotifications(unread);
  }, [notifications]);

  const handleMarkRead = useCallback(
    async (id: string) => {
      await markRead(id);
      refresh();
    },
    [refresh]
  );

  const handleMarkAllRead = useCallback(async () => {
    await markAllRead();
    refresh();
  }, [refresh]);

  if (loading) {
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
          message={error}
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
          <View style={styles.markAllReadContainer}>
            <Button
              title="Mark all read"
              variant="ghost"
              size="sm"
              onPress={handleMarkAllRead}
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
                  onPress={() => !n.readAt && handleMarkRead(n.id)}
                  testID={`notification-${n.id}`}
                  style={[
                    styles.notificationRow,
                    { opacity: n.readAt ? 0.6 : 1 },
                  ]}
                >
                  <View style={styles.notificationContent}>
                    <View style={styles.titleRow}>
                      <Text style={styles.notificationTitle}>{n.title}</Text>
                      <View style={styles.badgeWrapper}>
                        <Badge
                          variant={
                            PRIORITY_COLORS[n.priority] as
                              | "destructive"
                              | "secondary"
                              | "outline"
                          }
                        >
                          {n.priority}
                        </Badge>
                      </View>
                    </View>
                    <Text style={styles.body}>{n.body}</Text>
                    <Text style={styles.timestamp}>
                      {formatTimestamp(n.createdAt)}
                    </Text>
                  </View>
                  {!n.readAt ? (
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
  markAllReadContainer: {
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
    alignItems: "center",
  },
  notificationTitle: {
    fontWeight: "bold",
  },
  badgeWrapper: {
    marginLeft: 8,
  },
  body: {
    fontSize: 14,
    color: "#6B7280",
    marginTop: 4,
  },
  timestamp: {
    fontSize: 12,
    color: "#9CA3AF",
    marginTop: 4,
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
