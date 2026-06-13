"use client";

import { Loader2, Sparkles } from "lucide-react";
import Link from "next/link";
import { useAssistantNotifications } from "@/hooks/queries/useAssistantNotifications";

/**
 * Surfaces BFF `/internal/v1/assistant/notifications` on the telemedicine hub (work-mode + facility aware).
 * Empty and error states are explicit — no fabricated clinical alerts.
 */
export function TelemedicineAssistantSignals() {
  const { data = [], isLoading, isError } = useAssistantNotifications(true);

  return (
    <div className="mb-6 rounded-2xl border border-violet-200 bg-violet-50/60 p-4">
      <div className="flex items-center gap-2 text-violet-900">
        <Sparkles className="h-4 w-4 shrink-0" />
        <h3 className="text-sm font-semibold">Context signals (assistant)</h3>
      </div>
      <p className="mt-1 text-xs text-violet-800/90">
        Aggregated from <code className="rounded bg-card/70 px-1">GET /internal/v1/assistant/notifications</code> using
        your work mode, facility, and shift. Policy evaluation remains on Tshepo (
        <code className="rounded bg-card/70 px-1">/api/v1/policies</code> via gateway).
      </p>
      {isLoading ? (
        <div className="mt-3 flex items-center gap-2 text-xs text-violet-700">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading signals…
        </div>
      ) : isError ? (
        <p className="mt-3 text-xs text-warning-foreground">
          Signals could not be loaded. Clinical work continues; retry from the shell notification tray when available.
        </p>
      ) : data.length === 0 ? (
        <p className="mt-3 text-xs text-violet-800">
          No contextual signals returned for this workplace slice — this is a valid empty state, not a silent failure.
        </p>
      ) : (
        <ul className="mt-3 space-y-2">
          {data.slice(0, 5).map((n) => (
            <li key={n.id} className="rounded-lg border border-violet-100 bg-card/80 px-3 py-2 text-xs text-foreground">
              <span className="font-medium text-foreground">{n.title}</span>
              <span className="block text-muted-foreground">{n.body}</span>
              {n.action?.href ? (
                <Link href={n.action.href} className="mt-1 inline-block text-violet-700 underline">
                  {n.action.label}
                </Link>
              ) : null}
            </li>
          ))}
        </ul>
      )}
      <p className="mt-3 text-[11px] text-violet-800/80">
        Operational comms (handoffs, pages) stay in the{" "}
        <Link href="/settings/notifications" className="font-medium text-violet-900 underline">
          Comms Hub
        </Link>{" "}
        when wired to your tenant.
      </p>
    </div>
  );
}
