"use client";

/**
 * /social/moderation — Admin/moderator queue.
 * Lists open moderation cases with resolution controls.
 */

import { useState } from "react";
import { Flag } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useModerationQueue, useResolveModeration } from "@/hooks/queries/useSocial";

const DECISIONS = ["NO_ACTION", "REMOVE", "RESTRICT", "ARCHIVE", "ESCALATE"] as const;

export default function ModerationQueuePage() {
  const queueQ = useModerationQueue();
  const resolve = useResolveModeration();
  const [rationale, setRationale] = useState<Record<string, string>>({});

  const updateRationale = (id: string, value: string) => setRationale((p) => ({ ...p, [id]: value }));

  return (
    <AppLayout>
      <PageShell title="Moderation queue" subtitle="Review reported posts and resolve with an audit trail" icon={<Flag className="h-6 w-6" />}>
        {queueQ.isLoading && <p className="text-sm text-[color:var(--text-muted)]">Loading queue…</p>}
        {queueQ.data && queueQ.data.length === 0 && (
          <div className="impilo-surface-card p-8 text-center">
            <p className="text-sm font-semibold text-[color:var(--text-primary)]">Queue is clear</p>
            <p className="mt-1 text-xs text-[color:var(--text-muted)]">No open moderation cases right now.</p>
          </div>
        )}
        <div className="space-y-3">
          {queueQ.data?.map((c) => (
            <div key={c.id} className="impilo-surface-card space-y-3 p-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-xs uppercase tracking-wide text-[color:var(--text-muted)]">Case {c.id.slice(0, 8)}</p>
                  <p className="text-sm font-semibold text-[color:var(--text-primary)]">{c.reason}</p>
                  {c.details && <p className="mt-1 text-xs text-[color:var(--text-secondary)]">{c.details}</p>}
                </div>
                <span className="rounded-full border border-amber-300 bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-700">
                  {c.status}
                </span>
              </div>
              <p className="text-xs text-[color:var(--text-muted)]">
                Post: <code className="font-mono">{c.postId}</code> · Reporter: <code className="font-mono">{c.reporterActorId}</code>
              </p>
              <textarea
                rows={2}
                value={rationale[c.id] ?? ""}
                onChange={(e) => updateRationale(c.id, e.target.value)}
                placeholder="Moderator rationale (recorded in audit log)"
                className="w-full rounded-lg border border-[color:var(--border-soft)] bg-[color:var(--surface)] px-2.5 py-1.5 text-xs focus:border-[color:var(--primary)] focus:outline-none"
              />
              <div className="flex flex-wrap gap-2">
                {DECISIONS.map((d) => (
                  <button
                    key={d}
                    onClick={() => resolve.mutate({ caseId: c.id, decision: d, rationale: rationale[c.id] })}
                    disabled={resolve.isPending}
                    className="rounded-lg border border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] px-2.5 py-1 text-xs font-medium text-[color:var(--text-secondary)] hover:border-[color:var(--primary-muted)] hover:text-[color:var(--primary-hover)] disabled:opacity-50"
                  >
                    {d.replace(/_/g, " ")}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
