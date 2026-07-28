"use client";

/**
 * Mental Health — psychiatric emergency referral queue.
 * Route: /work/mental-health
 *
 * The accepting side of a psychiatric emergency handover (pct's emergency_handover,
 * target_type=MENTAL_HEALTH): lists PENDING referrals and lets a clinician accept or decline,
 * which writes the decision back onto pct's handover. The fuller clinical-record surface
 * (assessment, safety plan, involuntary episode, restraint events, admission requests, follow-up)
 * is deferred to W15's experience layer — this page covers the W13 acceptance surface only.
 */

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Brain, Check, X } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface Referral {
  id: string;
  episode_id: string;
  handover_id: string;
  subject_cpid: string | null;
  status: "PENDING" | "ACCEPTED" | "DECLINED";
  request_reason: string | null;
  requested_by: string;
  requested_at: string;
}

export default function MentalHealthQueuePage() {
  const queryClient = useQueryClient();
  const [decliningId, setDecliningId] = useState<string | null>(null);
  const [declineReason, setDeclineReason] = useState("");

  const { data, isLoading, isError } = useQuery<{ data: Referral[] }>({
    queryKey: ["mental-health-referrals", "PENDING"],
    queryFn: () => apiClient.get(`/internal/v1/mental-health/referrals?status=PENDING`),
  });

  const accept = useMutation({
    mutationFn: (id: string) => apiClient.post(`/internal/v1/mental-health/referrals/${id}/accept`, {}),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["mental-health-referrals"] }),
  });

  const decline = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      apiClient.post(`/internal/v1/mental-health/referrals/${id}/decline`, { reason }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["mental-health-referrals"] });
      setDecliningId(null);
      setDeclineReason("");
    },
  });

  const referrals = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Mental Health"
        subtitle="Psychiatric emergency referrals awaiting acceptance"
      >
        {isLoading ? (
          <div className="flex items-center gap-2 py-12 text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading referrals…
          </div>
        ) : isError ? (
          <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-900">
            Referral queue could not be read. This is not the same as an empty queue — retry before
            assuming there is nothing pending.
          </div>
        ) : referrals.length === 0 ? (
          <div className="rounded-lg border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
            <Brain className="mx-auto mb-2 h-6 w-6 text-muted-foreground" />
            No referrals pending acceptance.
          </div>
        ) : (
          <ul className="space-y-3">
            {referrals.map((r) => (
              <li key={r.id} className="rounded-lg border border-border p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="font-medium">
                      Episode {r.episode_id.slice(0, 8)}
                      {r.subject_cpid ? ` · ${r.subject_cpid}` : " · identity unresolved"}
                    </div>
                    <div className="mt-1 text-sm text-muted-foreground">
                      {r.request_reason ?? "No reason given"}
                    </div>
                    <div className="mt-1 text-xs text-muted-foreground">
                      Requested by {r.requested_by} · {new Date(r.requested_at).toLocaleString()}
                    </div>
                  </div>
                  <div className="flex shrink-0 gap-2">
                    <button
                      type="button"
                      disabled={accept.isPending}
                      onClick={() => accept.mutate(r.id)}
                      className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-white hover:bg-primary-hover disabled:opacity-50"
                    >
                      <Check className="h-3.5 w-3.5" /> Accept
                    </button>
                    <button
                      type="button"
                      onClick={() => setDecliningId(decliningId === r.id ? null : r.id)}
                      className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-sm font-medium hover:bg-muted"
                    >
                      <X className="h-3.5 w-3.5" /> Decline
                    </button>
                  </div>
                </div>

                {decliningId === r.id && (
                  <div className="mt-3 flex gap-2 border-t border-border pt-3">
                    <input
                      type="text"
                      value={declineReason}
                      onChange={(e) => setDeclineReason(e.target.value)}
                      placeholder="Decline reason (required)"
                      className="flex-1 rounded-lg border border-border px-3 py-1.5 text-sm"
                    />
                    <button
                      type="button"
                      disabled={!declineReason.trim() || decline.isPending}
                      onClick={() => decline.mutate({ id: r.id, reason: declineReason.trim() })}
                      className="rounded-lg bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
                    >
                      Confirm decline
                    </button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </PageShell>
    </AppLayout>
  );
}
