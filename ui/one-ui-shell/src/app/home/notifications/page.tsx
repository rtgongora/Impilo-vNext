"use client";

/**
 * Notifications — View and manage system notifications.
 * Route: /home/notifications
 *
 * Citizen inbox for IN_APP notifications served by the notification sovereign
 * service through the BFF (GET /internal/v1/notifications). Items arrive as
 * flat NotificationResponse rows ({ id, templateKey, channel, status,
 * createdAt, readAt, ... }); legacy attribute-shaped rows are tolerated.
 */

import {
  Loader2,
  AlertTriangle,
  BellOff,
  CalendarDays,
  Check,
  CheckCircle2,
  Mail,
  MessageSquare,
  AlertCircle,
  Info,
  PauseCircle,
  Users,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useNotifications, useMarkNotificationRead } from "@/hooks/queries/useNotifications";

type IconComponent = typeof Info;

const TYPE_ICONS: Record<string, { icon: IconComponent; className: string }> = {
  INFO: { icon: Info, className: "bg-primary-soft text-primary" },
  WARNING: { icon: AlertCircle, className: "bg-amber-100 text-amber-600" },
  ERROR: { icon: AlertTriangle, className: "bg-red-100 text-red-600" },
  SUCCESS: { icon: Check, className: "bg-green-100 text-green-600" },
  MESSAGE: { icon: Mail, className: "bg-purple-100 text-purple-600" },
};

/**
 * Template-key labels for citizen inbox items. The list API returns the
 * template key only (no rendered body), so the label is the honest display
 * text; it never fabricates tokens or details that are not in the payload.
 */
const TEMPLATE_META: Record<string, { label: string; icon: IconComponent; className: string }> = {
  // Appointment lifecycle (booking comms)
  APPOINTMENT_CITIZEN_REQUESTED: { label: "Appointment requested", icon: CalendarDays, className: "bg-primary-soft text-primary" },
  APPOINTMENT_CITIZEN_CONFIRMED: { label: "Appointment confirmed", icon: CalendarDays, className: "bg-green-100 text-green-600" },
  APPOINTMENT_CITIZEN_REJECTED: { label: "Appointment not approved", icon: CalendarDays, className: "bg-red-100 text-red-600" },
  APPOINTMENT_CITIZEN_CANCELLED: { label: "Appointment cancelled", icon: CalendarDays, className: "bg-neutral-100 text-muted-foreground" },
  APPOINTMENT_CITIZEN_RESCHEDULED: { label: "Appointment rescheduled", icon: CalendarDays, className: "bg-amber-100 text-amber-600" },
  APPOINTMENT_CITIZEN_REMINDER: { label: "Appointment reminder", icon: CalendarDays, className: "bg-primary-soft text-primary" },
  APPOINTMENT_CITIZEN_CHECKED_IN: { label: "Appointment checked in", icon: CheckCircle2, className: "bg-green-100 text-green-600" },
  APPOINTMENT_CITIZEN_MESSAGE: { label: "Message about your appointment", icon: MessageSquare, className: "bg-purple-100 text-purple-600" },
  // Queue lifecycle (PCT queue events)
  QUEUE_CITIZEN_JOINED: { label: "You joined the queue", icon: Users, className: "bg-primary-soft text-primary" },
  QUEUE_CITIZEN_CALLED: { label: "It is your turn — please proceed", icon: Users, className: "bg-green-100 text-green-600" },
  QUEUE_CITIZEN_TRANSFERRED: { label: "Your queue has changed", icon: Users, className: "bg-purple-100 text-purple-600" },
  QUEUE_CITIZEN_PRIORITISED: { label: "Your place in the queue was prioritised", icon: AlertCircle, className: "bg-amber-100 text-amber-600" },
  QUEUE_CITIZEN_NO_SHOW: { label: "We missed you in the queue", icon: AlertTriangle, className: "bg-red-100 text-red-600" },
  QUEUE_CITIZEN_COMPLETED: { label: "Your visit is complete", icon: CheckCircle2, className: "bg-green-100 text-green-600" },
  QUEUE_CITIZEN_ON_HOLD: { label: "Your queue place is on hold", icon: PauseCircle, className: "bg-amber-100 text-amber-600" },
};

interface InboxItem {
  id: string;
  title: string;
  message: string | null;
  icon: IconComponent;
  iconClassName: string;
  read: boolean;
  createdAt: string | null;
  channel: string | null;
}

function humanizeTemplateKey(key: string): string {
  return key
    .replace(/_SMS$/, "")
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/^./, (c) => c.toUpperCase());
}

function readStr(source: Record<string, unknown>, keys: string[]): string | null {
  for (const key of keys) {
    const value = source[key];
    if (typeof value === "string" && value.trim()) return value;
  }
  return null;
}

/** Tolerates both flat NotificationResponse rows and legacy attribute-shaped rows. */
function normalizeNotification(row: unknown, index: number): InboxItem | null {
  if (!row || typeof row !== "object") return null;
  const o = row as Record<string, unknown>;
  const attrs =
    o.attributes && typeof o.attributes === "object"
      ? (o.attributes as Record<string, unknown>)
      : o;

  const id = String(o.id ?? attrs.id ?? `notification-${index}`);
  const templateKey = readStr(attrs, ["templateKey", "template_key"]);
  const baseKey = templateKey ? templateKey.replace(/_SMS$/, "") : null;
  const templateMeta = baseKey ? TEMPLATE_META[baseKey] : undefined;

  const explicitTitle = readStr(attrs, ["title", "subject"]);
  const title =
    explicitTitle ??
    templateMeta?.label ??
    (templateKey ? humanizeTemplateKey(templateKey) : "Notification");

  const typeMeta =
    TYPE_ICONS[readStr(attrs, ["notificationType", "notification_type"]) ?? ""] ?? undefined;
  const meta = templateMeta ?? typeMeta ?? TYPE_ICONS.INFO;

  const readFlag = attrs.read;
  const readAt = readStr(attrs, ["readAt", "read_at"]);
  const read = typeof readFlag === "boolean" ? readFlag : readAt != null;

  return {
    id,
    title,
    // Only show a body the payload actually carries — never fabricate one.
    message: readStr(attrs, ["message", "body"]),
    icon: meta.icon,
    iconClassName: meta.className,
    read,
    createdAt: readStr(attrs, ["createdAt", "created_at", "sentAt", "sent_at"]),
    channel: readStr(attrs, ["channel"]),
  };
}

export default function NotificationsPage() {
  const queryClient = useQueryClient();

  const { data, isLoading, error } = useNotifications();
  const markAsRead = useMarkNotificationRead();

  const markAllRead = useMutation({
    mutationFn: () =>
      apiClient.post<ApiResponse<unknown>>("/internal/v1/notifications/read-all"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
  });

  const notifications = (Array.isArray(data?.data) ? data.data : [])
    .map(normalizeNotification)
    .filter((n): n is InboxItem => n !== null);
  const unreadCount = notifications.filter((n) => !n.read).length;

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
              const Icon = notification.icon;
              return (
                <div
                  key={notification.id}
                  className={`bg-card rounded-lg border p-4 flex items-start gap-3 transition-colors ${
                    notification.read
                      ? "border-border"
                      : "border-primary/25 bg-primary-soft/30"
                  }`}
                >
                  <div
                    className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 ${notification.iconClassName}`}
                  >
                    <Icon className="w-4 h-4" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-start justify-between gap-2">
                      <h4
                        className={`text-sm ${
                          notification.read
                            ? "text-foreground"
                            : "text-foreground font-medium"
                        }`}
                      >
                        {notification.title}
                      </h4>
                      {notification.createdAt && (
                        <span className="text-xs text-muted-foreground shrink-0">
                          {new Date(notification.createdAt).toLocaleString()}
                        </span>
                      )}
                    </div>
                    {notification.message && (
                      <p className="text-xs text-muted-foreground mt-0.5">{notification.message}</p>
                    )}
                    {notification.channel && notification.channel !== "IN_APP" && (
                      <span className="mt-1 inline-block rounded-full bg-neutral-100 px-2 py-0.5 text-[10px] font-medium text-muted-foreground">
                        {notification.channel}
                      </span>
                    )}
                  </div>
                  {!notification.read && (
                    <button
                      onClick={() => markAsRead.mutate(notification.id)}
                      disabled={markAsRead.isPending}
                      className="shrink-0 text-xs text-primary hover:text-primary-hover"
                      title="Mark as read"
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
