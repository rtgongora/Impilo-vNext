"use client";

import { useMemo, useState } from "react";
import { ClipboardList, HeartPulse, Loader2, Search, Send } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useWorkbenchClientSummary,
  useCreateWorkbenchFollowUp,
} from "@/hooks/queries/useSimbaWellnessCore";

function asRows(payload: unknown): Array<Record<string, unknown>> {
  if (Array.isArray(payload)) return payload as Array<Record<string, unknown>>;
  const data = (payload as { data?: unknown })?.data ?? payload;
  return Array.isArray(data) ? (data as Array<Record<string, unknown>>) : [];
}

/** Provider wellness workbench — look up an approved client, review their wellness, act on it. */
export default function ProviderWellnessWorkbenchPage() {
  const [cpidInput, setCpidInput] = useState("");
  const [activeCpid, setActiveCpid] = useState<string | null>(null);

  const summaryQ = useWorkbenchClientSummary(activeCpid);
  const createFollowUp = useCreateWorkbenchFollowUp();

  const [fuTitle, setFuTitle] = useState("");
  const [fuDetail, setFuDetail] = useState("");

  const summary = summaryQ.data?.data as Record<string, unknown> | undefined;
  const assessments = useMemo(() => asRows(summary?.assessments), [summary]);
  const followUps = useMemo(() => asRows(summary?.follow_ups), [summary]);
  const careLinkages = useMemo(() => asRows(summary?.care_linkages), [summary]);
  const latestAssessment = assessments[0];

  function submitFollowUp() {
    if (!activeCpid || !fuTitle.trim()) return;
    createFollowUp.mutate(
      { cpid: activeCpid, title: fuTitle, detail: fuDetail, action_type: "REVIEW" },
      {
        onSuccess: () => {
          setFuTitle("");
          setFuDetail("");
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Wellness workbench"
        subtitle="Review and support the wellness of your approved clients."
        icon={<HeartPulse className="h-6 w-6" />}
      >
        <div className="mx-auto max-w-3xl space-y-6">
          <div className="flex gap-2">
            <input
              value={cpidInput}
              onChange={(e) => setCpidInput(e.target.value)}
              placeholder="Client CPID"
              className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm"
            />
            <button
              type="button"
              onClick={() => setActiveCpid(cpidInput.trim() || null)}
              className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white"
            >
              <Search className="h-4 w-4" />
              Load
            </button>
          </div>

          {summaryQ.isLoading && activeCpid && (
            <div className="flex items-center justify-center py-16 text-slate-400">
              <Loader2 className="h-6 w-6 animate-spin" />
            </div>
          )}

          {summary && (
            <>
              <section className="rounded-xl border border-slate-200 bg-white p-6">
                <h2 className="mb-3 text-lg font-semibold">Latest assessment</h2>
                {latestAssessment ? (
                  <div className="flex items-center gap-3">
                    <span className="rounded-full bg-slate-100 px-3 py-1 text-sm font-medium text-slate-700">
                      {String(latestAssessment.riskBand ?? "Not scored")}
                    </span>
                    <span className="text-sm text-slate-500">
                      {String(latestAssessment.status)}
                    </span>
                  </div>
                ) : (
                  <p className="text-sm text-slate-500">No assessment on record.</p>
                )}
              </section>

              <section className="rounded-xl border border-slate-200 bg-white p-6">
                <h2 className="mb-3 flex items-center gap-2 text-lg font-semibold">
                  <ClipboardList className="h-5 w-5" /> Follow-ups
                </h2>
                {followUps.length === 0 ? (
                  <p className="text-sm text-slate-500">No follow-ups yet.</p>
                ) : (
                  <ul className="space-y-2">
                    {followUps.map((f) => (
                      <li
                        key={String(f.followUpId)}
                        className="flex items-center justify-between rounded-lg border border-slate-100 px-3 py-2 text-sm"
                      >
                        <span>{String(f.title)}</span>
                        <span className="text-xs text-slate-400">{String(f.status)}</span>
                      </li>
                    ))}
                  </ul>
                )}
                {careLinkages.length > 0 && (
                  <p className="mt-3 text-xs text-amber-600">
                    {careLinkages.length} open care linkage(s) — clinical follow-up owned by PCT.
                  </p>
                )}
              </section>

              <section className="rounded-xl border border-slate-200 bg-white p-6">
                <h2 className="mb-3 text-lg font-semibold">Create a follow-up</h2>
                <div className="space-y-3">
                  <input
                    value={fuTitle}
                    onChange={(e) => setFuTitle(e.target.value)}
                    placeholder="Follow-up title"
                    className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                  />
                  <textarea
                    value={fuDetail}
                    onChange={(e) => setFuDetail(e.target.value)}
                    placeholder="Details (optional)"
                    rows={3}
                    className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                  />
                  <button
                    type="button"
                    onClick={submitFollowUp}
                    disabled={createFollowUp.isPending || !fuTitle.trim()}
                    className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
                  >
                    {createFollowUp.isPending ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <Send className="h-4 w-4" />
                    )}
                    Create follow-up
                  </button>
                </div>
              </section>
            </>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
