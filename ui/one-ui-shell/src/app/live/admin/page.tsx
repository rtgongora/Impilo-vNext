"use client";

import Link from "next/link";
import { Activity, BarChart3, Calendar, Radio, Shield } from "lucide-react";
import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";
import { LiveEventList } from "@/components/live/LiveEventList";
import { useLiveEvents } from "@/hooks/queries/useLive";

/**
 * Platform Live operations dashboard — moderation queue entry, technical health, cross-mode overview.
 */
export default function LiveAdminPage() {
  const scheduled = useLiveEvents("SCHEDULED");
  const live = useLiveEvents("LIVE");
  const ended = useLiveEvents("ENDED");

  return (
    <AppLayout>
      <PageShell
        title="Impilo Live Administration"
        subtitle="Governed live engagement layer — events, broadcasts, clinical sessions and webinars"
        icon={<Shield className="h-6 w-6" />}
      >
        <div className="grid gap-4 sm:grid-cols-3 mb-6">
          <StatCard icon={<Calendar className="h-5 w-5" />} label="Scheduled" value={scheduled.data?.length ?? 0} />
          <StatCard icon={<Radio className="h-5 w-5" />} label="Live now" value={live.data?.length ?? 0} highlight />
          <StatCard icon={<Activity className="h-5 w-5" />} label="Recently ended" value={ended.data?.length ?? 0} />
        </div>

        <div className="grid lg:grid-cols-2 gap-6">
          <section className="rounded-2xl border border-border bg-card p-5">
            <h2 className="font-semibold text-foreground flex items-center gap-2">
              <Radio className="h-4 w-4 text-red-600" /> Live now
            </h2>
            <div className="mt-4">
              <LiveEventList events={live.data ?? []} emptyMessage="No sessions are live." />
            </div>
          </section>

          <section className="rounded-2xl border border-border bg-card p-5">
            <h2 className="font-semibold text-foreground flex items-center gap-2">
              <Calendar className="h-4 w-4" /> Upcoming
            </h2>
            <div className="mt-4">
              <LiveEventList events={scheduled.data ?? []} emptyMessage="No scheduled events." />
            </div>
          </section>
        </div>

        <section className="mt-6 rounded-2xl border border-border bg-card p-5">
          <h2 className="font-semibold text-foreground flex items-center gap-2 mb-4">
            <BarChart3 className="h-4 w-4" /> Operations
          </h2>
          <div className="flex flex-wrap gap-3 text-sm">
            <Link href="/live/manage" className="rounded-lg border px-3 py-2 hover:bg-background">
              Event management & moderation
            </Link>
            <Link href="/live/create" className="rounded-lg border px-3 py-2 hover:bg-background">
              Create event
            </Link>
            <Link href="/live/replays" className="rounded-lg border px-3 py-2 hover:bg-background">
              Replay library
            </Link>
            <Link href="/live/cpd" className="rounded-lg border px-3 py-2 hover:bg-background">
              CPD & certificates
            </Link>
          </div>
          <p className="mt-4 text-xs text-muted-foreground">
            Impilo Live is the governed live engagement layer of the Impilo Health OS — clinical sessions,
            professional meetings, CPD webinars, public health broadcasts, hybrid events and emergency
            briefings through one secure, role-aware service.
          </p>
        </section>
      </PageShell>
    </AppLayout>
  );
}

function StatCard({
  icon,
  label,
  value,
  highlight,
}: {
  icon: React.ReactNode;
  label: string;
  value: number;
  highlight?: boolean;
}) {
  return (
    <div
      className={`rounded-xl border p-4 ${highlight ? "border-danger/28 bg-danger-soft" : "border-border bg-card"}`}
    >
      <div className="flex items-center gap-2 text-muted-foreground text-sm">{icon}{label}</div>
      <p className="mt-2 text-2xl font-semibold text-foreground">{value}</p>
    </div>
  );
}
