"use client";

import { Bell, Check, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useMarkNotificationRead,
  useSocialNotifications,
  type SocialNotification,
} from "@/hooks/queries/useSimbaSocial";

/** Wellness-social notifications inbox. Delivery is owned by Khuluma; this shows the persisted events. */
export default function SocialNotificationsPage() {
  const notifQ = useSocialNotifications();
  const markRead = useMarkNotificationRead();
  const notifications: SocialNotification[] = notifQ.data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Notifications"
        subtitle="Reactions, comments, follows, announcements and moderation updates."
        icon={<Bell className="h-6 w-6" />}
      >
        {notifQ.isLoading ? (
          <div className="flex items-center gap-2 p-8 text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading…
          </div>
        ) : notifications.length === 0 ? (
          <div className="rounded-lg border border-border bg-card p-8 text-center text-muted-foreground">
            You&apos;re all caught up.
          </div>
        ) : (
          <ul className="space-y-2">
            {notifications.map((n) => (
              <li
                key={n.notificationId}
                className={`flex items-start justify-between gap-3 rounded-lg border border-border p-3 ${
                  n.status === "UNREAD" ? "bg-primary/5" : "bg-card"
                }`}
              >
                <div className="min-w-0">
                  <span className="rounded bg-accent px-1.5 py-0.5 text-xs">{n.notificationType}</span>
                  <p className="mt-1 text-sm">{n.summary ?? "New activity"}</p>
                  <p className="text-xs text-muted-foreground">
                    {new Date(n.createdAt).toLocaleString()}
                  </p>
                </div>
                {n.status === "UNREAD" && (
                  <button
                    onClick={() => markRead.mutate({ notificationId: n.notificationId })}
                    className="inline-flex items-center gap-1 rounded-md border border-border px-2 py-1 text-xs hover:bg-accent"
                  >
                    <Check className="h-3 w-3" /> Mark read
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </PageShell>
    </AppLayout>
  );
}
