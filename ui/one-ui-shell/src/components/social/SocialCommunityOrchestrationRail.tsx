"use client";

import Link from "next/link";
import { Loader2, Megaphone, MessageSquare, ShieldAlert } from "lucide-react";
import { useSocialFeed } from "@/hooks/queries/useSocial";
import { useAlerts } from "@/hooks/queries/useSurveillance";

export function SocialCommunityOrchestrationRail() {
  const feedQ = useSocialFeed("home");
  const alertsQ = useAlerts();
  const postCount = feedQ.data?.items?.length ?? 0;
  const alerts = alertsQ.data ?? [];
  const highPriority = alerts.filter((a) => /high|critical|emergency/i.test(a.severity)).length;

  return (
    <section
      className="mb-4 rounded-2xl border border-sky-200 bg-sky-50/80 px-4 py-3"
      data-testid="social-community-orchestration-rail"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 text-sm font-semibold text-sky-900">
            <MessageSquare className="h-4 w-4" />
            Social timeline orchestration
          </p>
          <p className="mt-1 text-xs text-sky-800" data-testid="social-feed-stats">
            {feedQ.isLoading ? "Loading feed…" : `${postCount} post(s) in home scope`}
            {alertsQ.isLoading ? "" : ` · ${alerts.length} public-health alert(s)`}
            {highPriority > 0 ? ` · ${highPriority} high priority` : ""}
          </p>
        </div>
        <div className="flex flex-wrap gap-2 text-xs">
          <Link
            href="/social/drafts"
            className="rounded-lg border border-sky-200 bg-card px-2.5 py-1.5 font-medium text-sky-900 hover:border-sky-300"
          >
            Drafts
          </Link>
          <Link
            href="/public-health?tab=surveillance"
            className="inline-flex items-center gap-1 rounded-lg border border-sky-200 bg-card px-2.5 py-1.5 font-medium text-sky-900 hover:border-sky-300"
          >
            <ShieldAlert className="h-3.5 w-3.5" />
            Surveillance
          </Link>
          <Link
            href="/social/moderation"
            className="inline-flex items-center gap-1 rounded-lg border border-sky-200 bg-card px-2.5 py-1.5 font-medium text-sky-900 hover:border-sky-300"
          >
            <Megaphone className="h-3.5 w-3.5" />
            Moderation
          </Link>
        </div>
      </div>
      {(feedQ.isLoading || alertsQ.isLoading) && (
        <div className="mt-2 flex items-center gap-2 text-xs text-sky-700">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Binding community + surveillance planes…
        </div>
      )}
    </section>
  );
}
