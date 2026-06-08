"use client";

import Link from "next/link";
import { ArrowRight, Bell, Loader2, Megaphone, Radio } from "lucide-react";
import { useOmnichannelCampaigns, useOmnichannelDashboard } from "@/hooks/queries/useOmnichannel";
import { useNotificationTemplates } from "@/hooks/queries/useNotificationTemplates";
import { useNotifications } from "@/hooks/queries/useNotifications";

function metricFromDashboard(metrics: Record<string, unknown>, ...keys: string[]): number {
  for (const key of keys) {
    const direct = metrics[key];
    if (typeof direct === "number") return direct;
    for (const section of ["communications", "operations", "executive", "clinical"]) {
      const nested = (metrics[section] as Record<string, unknown> | undefined)?.[key];
      if (typeof nested === "number") return nested;
    }
  }
  return 0;
}

export function NotificationCommsOrchestrationRail() {
  const { data: dashboard, isLoading: dashLoading } = useOmnichannelDashboard();
  const { data: notifications, isLoading: notifLoading } = useNotifications();
  const { data: campaigns, isLoading: campaignsLoading } = useOmnichannelCampaigns();
  const { data: templates, isLoading: templatesLoading } = useNotificationTemplates();

  const metrics = (dashboard?.data ?? {}) as Record<string, unknown>;
  const activeCampaigns = metricFromDashboard(
    metrics,
    "active_campaigns",
    "activeCampaigns",
    "campaigns_active",
  );
  const completedCampaigns = metricFromDashboard(
    metrics,
    "completed_campaigns",
    "completedCampaigns",
  );
  const configuredChannels = metricFromDashboard(metrics, "configured_channels", "configuredChannels");

  const campaignRows = campaigns ?? [];
  const templateRows = templates ?? [];
  const notifList = Array.isArray(notifications?.data) ? notifications.data : [];
  const unread = notifList.filter((n) => {
    if (!n || typeof n !== "object") return false;
    const row = n as Record<string, unknown>;
    return row.read === false || (row.attributes && (row.attributes as Record<string, unknown>).read === false);
  }).length;

  const loading = dashLoading && notifLoading && campaignsLoading && templatesLoading;

  if (loading) {
    return (
      <section
        className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3"
        data-testid="notification-comms-orchestration-rail"
        aria-busy="true"
      >
        <div className="flex items-center gap-2 text-sm text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading notification &amp; comms orchestration…
        </div>
      </section>
    );
  }

  return (
    <section
      className="rounded-xl border border-teal-200 bg-teal-50/60 px-4 py-4"
      data-testid="notification-comms-orchestration-rail"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-2 text-sm text-teal-950">
          <Radio className="mt-0.5 h-4 w-4 shrink-0 text-teal-600" />
          <div>
            <p className="font-medium">Notification &amp; communications orchestration</p>
            <p>
              Compose campaigns on omnichannel tabs, govern templates, and track delivery via the notification inbox and
              channel dashboard.
            </p>
            <div
              className="mt-2 flex flex-wrap items-center gap-1 text-xs text-teal-800"
              data-testid="notification-comms-pipeline"
            >
              <Link href="/omnichannel?tab=campaigns" className="font-medium underline-offset-2 hover:underline">
                Compose
              </Link>
              <ArrowRight className="h-3 w-3" />
              <Link
                href="/admin/notifications/templates"
                className="font-medium underline-offset-2 hover:underline"
              >
                Templates ({templatesLoading ? "…" : templateRows.length})
              </Link>
              <ArrowRight className="h-3 w-3" />
              <span>
                Delivery · {activeCampaigns} active / {completedCampaigns} completed campaign(s) ·{" "}
                {configuredChannels} channel(s) · {notifList.length} inbox row(s)
                {unread > 0 ? ` · ${unread} unread` : ""}
              </span>
            </div>
            {!campaignsLoading && campaignRows.length > 0 ? (
              <p className="mt-1 text-xs text-teal-800" data-testid="notification-campaign-preview">
                Latest campaigns:{" "}
                {campaignRows
                  .slice(0, 3)
                  .map((c) => String(c.name ?? c.id ?? "campaign"))
                  .join(", ")}
              </p>
            ) : (
              <p className="mt-1 text-xs text-teal-800">
                No campaigns yet — start on the Campaigns tab or reuse a published template.
              </p>
            )}
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link
            href="/omnichannel?tab=campaigns"
            className="inline-flex items-center gap-1 rounded-lg bg-teal-700 px-3 py-1.5 text-xs font-medium text-white hover:bg-teal-800"
          >
            <Megaphone className="h-3.5 w-3.5" />
            Compose campaign
          </Link>
          <Link
            href="/admin/notifications/templates"
            className="inline-flex items-center gap-1 rounded-lg border border-teal-300 bg-white px-3 py-1.5 text-xs font-medium text-teal-900 hover:bg-teal-50"
          >
            <Bell className="h-3.5 w-3.5" />
            Templates
          </Link>
          <Link
            href="/communication"
            className="inline-flex items-center gap-1 rounded-lg border border-teal-300 bg-white px-3 py-1.5 text-xs font-medium text-teal-900 hover:bg-teal-50"
          >
            Secure messaging
          </Link>
        </div>
      </div>
    </section>
  );
}
