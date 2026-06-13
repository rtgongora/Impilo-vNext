"use client";

/**
 * Notifications — View and manage system notifications.
 * Route: /home/notifications
 */

import { Loader2, AlertTriangle, BellOff, Check, Mail, AlertCircle, Info } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface NotificationResource {
  id: string;
  type: "notification";
  attributes: {
    title: string;
    message: string;
    notificationType: string;
    read: boolean;
    createdAt: string;
    [key: string]: unknown;
  };
}

const TYPE_ICONS: Record<string, { icon: typeof Info; className: string }> = {
  INFO: { icon: Info, className: "bg-primary-soft text-primary" },
  WARNING: { icon: AlertCircle, className: "bg-amber-100 text-amber-600" },
  ERROR: { icon: AlertTriangle, className: "bg-red-100 text-red-600" },
  SUCCESS: { icon: Check, className: "bg-green-100 text-green-600" },
  MESSAGE: { icon: Mail, className: "bg-purple-100 text-purple-600" },
};

export default function NotificationsPage() {
  const queryClient = useQueryClient();

  const { data, isLoading, error } = useQuery<ApiResponse<NotificationResource[]>>({
    queryKey: ["notifications"],
    queryFn: () =>
      apiClient.get<ApiResponse<NotificationResource[]>>("/internal/v1/notifications"),
  });

  const markAsRead = useMutation({
    mutationFn: (id: string) =>
      apiClient.post<ApiResponse<unknown>>(`/internal/v1/notifications/${id}/read`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
  });

  const markAllRead = useMutation({
    mutationFn: () =>
      apiClient.post<ApiResponse<unknown>>("/internal/v1/notifications/read-all"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
  });

  const notifications = data?.data ?? [];
  const unreadCount = notifications.filter((n) => !n.attributes.read).length;

  return (
    <AppLayout>
      <PageShell title="Notifications" subtitle={`${unreadCount} unread notification${unreadCount !== 1 ? "s" : ""}`}>
        {/* Actions */}
        {notifications.length > 0 && unreadCount > 0 && (
          <div className="mb-4 flex justify-end">
            <button
              onClick={() => markAllRead.mutate()}
              disabled={markAllRead.isPending}
              className="text-xs text-primary hover:text-primary-hover font-medium"
            >
              Mark all as read
            </button>
          </div>
        )}

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading notifications...</span>
          </div>
        ) : error ? (
          <div className="bg-danger-soft rounded-lg border border-danger/28 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load notifications</p>
          </div>
        ) : notifications.length === 0 ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <BellOff className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">No notifications</p>
          </div>
        ) : (
          <div className="space-y-2">
            {notifications.map((notification) => {
              const typeConfig = TYPE_ICONS[notification.attributes.notificationType] ?? TYPE_ICONS.INFO;
              const Icon = typeConfig.icon;
              return (
                <div
                  key={notification.id}
                  className={`bg-card rounded-lg border p-4 flex items-start gap-3 transition-colors ${
                    notification.attributes.read
                      ? "border-border"
                      : "border-primary/25 bg-primary-soft/30"
                  }`}
                >
                  <div
                    className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 ${typeConfig.className}`}
                  >
                    <Icon className="w-4 h-4" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-start justify-between gap-2">
                      <h4
                        className={`text-sm ${
                          notification.attributes.read
                            ? "text-foreground"
                            : "text-foreground font-medium"
                        }`}
                      >
                        {notification.attributes.title}
                      </h4>
                      <span className="text-xs text-muted-foreground shrink-0">
                        {new Date(notification.attributes.createdAt).toLocaleString()}
                      </span>
                    </div>
                    <p className="text-xs text-muted-foreground mt-0.5">{notification.attributes.message}</p>
                  </div>
                  {!notification.attributes.read && (
                    <button
                      onClick={() => markAsRead.mutate(notification.id)}
                      disabled={markAsRead.isPending}
                      className="shrink-0 text-xs text-primary hover:text-primary-hover"
                    >
                      <Check className="w-4 h-4" />
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
