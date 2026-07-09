"use client";

/**
 * IATG Trust Console — unified governance review surface so IATG stops being invisible.
 * Route: /registry-admin/trust-console | guard: REGISTRY_ADMIN (route) + SYSTEM_ADMIN/HIE_ADMIN (ext_authz V032)
 *
 * Queues: pending provider access requests (varapi), pending facility admin claims (tuso),
 * pending org onboarding (admin governance), assurance upgrade queue (identity-assurance),
 * plus recent decisions. One dead downstream degrades its queue only — never the console.
 */

import { useState } from "react";
import Link from "next/link";
import { AlertTriangle, ArrowLeft, ShieldCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { PlaneTrustBanner } from "@/components/experience/PlaneTrustBanner";
import {
  useTrustConsoleQueue,
  useTrustConsoleSummary,
  type TrustConsoleQueueName,
} from "@/hooks/queries/useTrustConsole";
import { QUEUE_CONFIGS, queueConfig, type QueueItemView } from "./queue-config";
import { DecisionDrawer } from "./DecisionDrawer";
import { RecentDecisionsPanel } from "./RecentDecisionsPanel";

export default function TrustConsolePage() {
  const [activeQueue, setActiveQueue] = useState<TrustConsoleQueueName>("provider-access-requests");
  const [selected, setSelected] = useState<QueueItemView | null>(null);

  const summary = useTrustConsoleSummary();
  const queue = useTrustConsoleQueue(activeQueue);
  const config = queueConfig(activeQueue);

  const items: QueueItemView[] = (queue.data?.items ?? []).map(config.toView);

  return (
    <AppLayout>
      <PageShell
        title="Trust console"
        subtitle="IATG governance queues — review and decide access, claims, onboarding and assurance"
        serviceSlug="tshepo"
      >
        <div className="space-y-6">
          <Link
            href="/registry-admin"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="h-4 w-4" /> Back to registry administration
          </Link>

          <PlaneTrustBanner
            variant="registry"
            eyebrow="High-trust plane"
            title="Every decision here is recorded against the owning registry"
          >
            <p>
              Decisions pass through to the system of record (varapi, tuso, workforce governance,
              identity assurance) with your identity, a note, and an audit event. Nothing is decided
              in the experience layer.
            </p>
          </PlaneTrustBanner>

          {summary.data?.integrationStatus === "partial" && (
            <div className="flex items-start gap-2 rounded-md border border-warning/35 bg-warning-soft px-4 py-3 text-sm text-warning-foreground">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <span>{summary.data.friendlyMessage}</span>
            </div>
          )}
          {summary.data?.status === "denied" && (
            <div className="flex items-start gap-2 rounded-md border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <span>{summary.data.friendlyMessage ?? "You do not have authority for the Trust Console."}</span>
            </div>
          )}

          {/* Queue tabs with pending counts */}
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {QUEUE_CONFIGS.map((q) => {
              const tile = summary.data?.queues?.[q.name];
              const active = q.name === activeQueue;
              return (
                <button
                  key={q.name}
                  type="button"
                  onClick={() => {
                    setActiveQueue(q.name);
                    setSelected(null);
                  }}
                  className={`rounded-2xl border p-4 text-left shadow-sm transition ${
                    active
                      ? "border-amber-400 bg-amber-50/60"
                      : "border-warning/35/80 bg-card hover:border-amber-400"
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-sm font-medium text-foreground">{q.label}</span>
                    <ShieldCheck className={`h-4 w-4 ${active ? "text-warning-foreground" : "text-muted-foreground"}`} />
                  </div>
                  <div className="mt-2 text-2xl font-semibold text-foreground">
                    {summary.isLoading
                      ? "…"
                      : tile?.integrationStatus === "pending_backend"
                        ? "—"
                        : (tile?.pendingCount ?? 0)}
                  </div>
                  <div className="mt-0.5 text-xs text-muted-foreground">
                    {tile?.integrationStatus === "pending_backend" ? "backend unavailable" : "pending"}
                  </div>
                </button>
              );
            })}
          </div>

          <div className="grid gap-6 lg:grid-cols-3">
            {/* Active queue */}
            <div className="lg:col-span-2">
              <div className="overflow-hidden rounded-lg border border-border bg-card shadow-sm">
                <div className="border-b border-border bg-background px-4 py-3">
                  <div className="text-sm font-medium text-foreground">{config.label}</div>
                  <div className="text-xs text-muted-foreground">{config.description}</div>
                </div>
                {queue.isLoading ? (
                  <div className="px-4 py-8 text-sm text-muted-foreground">Loading…</div>
                ) : queue.data?.integrationStatus === "pending_backend" ? (
                  <div className="px-4 py-8 text-sm text-warning-foreground">
                    {queue.data.friendlyMessage ?? "This queue's backend is unavailable."}
                  </div>
                ) : queue.data?.status === "denied" ? (
                  <div className="px-4 py-8 text-sm text-rose-700">
                    {queue.data.friendlyMessage ?? "You do not have authority for this queue."}
                  </div>
                ) : items.length === 0 ? (
                  <div className="px-4 py-8 text-sm text-muted-foreground">
                    Nothing waiting for review in this queue.
                  </div>
                ) : (
                  <ul className="divide-y divide-slate-100">
                    {items.map((item) => (
                      <li key={item.id}>
                        <button
                          type="button"
                          onClick={() => setSelected(item)}
                          className="flex w-full items-center justify-between gap-4 px-4 py-3 text-left transition hover:bg-background"
                        >
                          <div className="min-w-0">
                            <div className="truncate font-medium text-foreground">{item.title}</div>
                            <div className="truncate text-xs text-muted-foreground">
                              {item.subtitle}
                              {item.createdAt ? ` · ${new Date(item.createdAt).toLocaleString()}` : ""}
                            </div>
                          </div>
                          <span className="shrink-0 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-warning-foreground">
                            {item.status}
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>

            {/* Recent decisions */}
            <div>
              <RecentDecisionsPanel />
            </div>
          </div>
        </div>

        {selected && (
          <DecisionDrawer
            queue={activeQueue}
            config={config}
            item={selected}
            onClose={() => setSelected(null)}
          />
        )}
      </PageShell>
    </AppLayout>
  );
}
