"use client";

/**
 * Trust Console recent-decisions panel: decided provider access requests
 * (varapi) and the governance audit feed, each degrading independently.
 */

import { History } from "lucide-react";
import { useTrustConsoleRecentDecisions } from "@/hooks/queries/useTrustConsole";

function s(value: unknown): string {
  return typeof value === "string" && value.length > 0 ? value : "—";
}

export function RecentDecisionsPanel() {
  const { data, isLoading } = useTrustConsoleRecentDecisions();

  const decisions = data?.providerAccessDecisions;
  const audit = data?.governanceAuditEvents;

  return (
    <div className="overflow-hidden rounded-lg border border-border bg-card shadow-sm">
      <div className="flex items-center gap-2 border-b border-border bg-background px-4 py-3 text-sm font-medium text-foreground">
        <History className="h-4 w-4 text-muted-foreground" />
        Recent decisions
      </div>
      {isLoading ? (
        <div className="px-4 py-6 text-sm text-muted-foreground">Loading…</div>
      ) : (
        <div className="divide-y divide-slate-100">
          <div className="px-4 py-3">
            <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              Provider access decisions
            </h4>
            {decisions?.integrationStatus === "pending_backend" ? (
              <p className="mt-2 text-sm text-warning-foreground">{decisions.friendlyMessage}</p>
            ) : !decisions?.items?.length ? (
              <p className="mt-2 text-sm text-muted-foreground">No decided requests yet.</p>
            ) : (
              <ul className="mt-2 space-y-2">
                {decisions.items.slice(0, 8).map((item, i) => (
                  <li key={`${s(item.publicId)}-${i}`} className="flex items-center justify-between gap-3 text-sm">
                    <span className="text-foreground">
                      {s(item.publicId)} · {s(item.requestType)}
                    </span>
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                        item.status === "APPROVED"
                          ? "bg-emerald-100 text-emerald-700"
                          : "bg-rose-100 text-rose-700"
                      }`}
                    >
                      {s(item.status)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <div className="px-4 py-3">
            <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              Governance audit feed
            </h4>
            {audit?.integrationStatus === "pending_backend" ? (
              <p className="mt-2 text-sm text-warning-foreground">{audit.friendlyMessage}</p>
            ) : !audit?.items?.length ? (
              <p className="mt-2 text-sm text-muted-foreground">No recent audit events.</p>
            ) : (
              <ul className="mt-2 space-y-2">
                {audit.items.slice(0, 8).map((event, i) => (
                  <li key={`${s(event.id)}-${i}`} className="flex items-center justify-between gap-3 text-sm">
                    <span className="truncate text-foreground">{s(event.eventType)}</span>
                    <span className="shrink-0 text-xs text-muted-foreground">{s(event.occurredAt)}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
