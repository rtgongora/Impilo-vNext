"use client";

/**
 * Care Alerts — Health OS §4
 * Notifications about dependants' health events.
 * Route: /caregiving/notifications | Zone: caregiving | Guard: auth
 */

import { Bell, Filter, CheckCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function CareNotificationsPage() {
  return (
    <AppLayout>
      <PageShell
        title="Care Alerts"
        subtitle="Notifications about your dependants' health events and care activities"
        icon={<Bell className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Actions bar */}
          <div className="flex items-center justify-between">
            <button className="inline-flex items-center gap-2 rounded-lg border border-border px-3 py-2 text-sm text-foreground hover:bg-background transition-colors">
              <Filter className="h-4 w-4" />
              Filter
            </button>
            <button className="inline-flex items-center gap-2 rounded-lg border border-border px-3 py-2 text-sm text-foreground hover:bg-background transition-colors">
              <CheckCheck className="h-4 w-4" />
              Mark all read
            </button>
          </div>

          {/* Empty state */}
          <div className="rounded-lg border border-dashed border-border bg-background p-12 text-center">
            <Bell className="mx-auto h-12 w-12 text-muted-foreground" />
            <h3 className="mt-4 text-sm font-semibold text-foreground">No care alerts</h3>
            <p className="mt-2 text-sm text-muted-foreground">
              Alerts about medication adherence, appointment reminders, and health changes for your dependants will appear here.
            </p>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
