"use client";

import { useState } from "react";
import { AlertTriangle, Loader2, ShieldCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useModerationAction,
  useModerationInbox,
  type ContentReport,
} from "@/hooks/queries/useSimbaSocial";

const ACTIONS = ["HIDDEN", "REMOVED", "FLAGGED", "ESCALATED", "RESTORED", "REJECTED"];

/** Social moderation inbox — provider/moderator workbench. Safety reports carry a care-linkage badge. */
export default function SocialModerationPage() {
  const [status, setStatus] = useState("OPEN");
  const inboxQ = useModerationInbox(status);
  const act = useModerationAction();
  const reports: ContentReport[] = inboxQ.data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Moderation inbox"
        subtitle="Review reported wellness-social content. Self-harm/abuse reports auto-escalate to care."
        icon={<ShieldCheck className="h-6 w-6" />}
      >
        <div className="mb-4 flex items-center gap-2">
          <span className="text-sm text-muted-foreground">
            Backlog: {inboxQ.data?.backlog ?? 0}
          </span>
          <select
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            className="ml-auto rounded-lg border border-border bg-background px-3 py-1.5 text-sm"
          >
            <option value="OPEN">Open</option>
            <option value="TRIAGED">Triaged</option>
            <option value="ACTIONED">Actioned</option>
            <option value="DISMISSED">Dismissed</option>
          </select>
        </div>

        {inboxQ.isLoading ? (
          <div className="flex items-center gap-2 p-8 text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading inbox…
          </div>
        ) : reports.length === 0 ? (
          <div className="rounded-lg border border-border bg-card p-8 text-center text-muted-foreground">
            No {status.toLowerCase()} reports.
          </div>
        ) : (
          <ul className="space-y-3">
            {reports.map((r) => (
              <li key={r.reportId} className="rounded-lg border border-border bg-card p-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="rounded bg-destructive/10 px-1.5 py-0.5 text-xs font-medium text-destructive">
                        {r.reason}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {r.subjectType} · {r.status}
                      </span>
                      {r.careLinkageId && (
                        <span className="inline-flex items-center gap-1 rounded bg-amber-100 px-1.5 py-0.5 text-xs text-amber-800">
                          <AlertTriangle className="h-3 w-3" /> Escalated to care
                        </span>
                      )}
                    </div>
                    {r.detail && <p className="mt-1 text-sm">{r.detail}</p>}
                    <p className="mt-1 font-mono text-xs text-muted-foreground">
                      subject {r.subjectId}
                    </p>
                  </div>
                </div>
                <div className="mt-3 flex flex-wrap gap-2">
                  {ACTIONS.map((a) => (
                    <button
                      key={a}
                      onClick={() => act.mutate({ reportId: r.reportId, action_state: a })}
                      disabled={act.isPending}
                      className="rounded-md border border-border px-2.5 py-1 text-xs hover:bg-accent disabled:opacity-60"
                    >
                      {a}
                    </button>
                  ))}
                </div>
              </li>
            ))}
          </ul>
        )}
      </PageShell>
    </AppLayout>
  );
}
