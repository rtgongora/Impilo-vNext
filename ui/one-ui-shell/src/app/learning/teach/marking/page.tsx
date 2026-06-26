"use client";

import { useState } from "react";
import { PageShell } from "@/components/PageShell";
import { useFundoMarkingQueue, useMarkSubmission } from "@/hooks/queries/useFundoAssignments";

type Submission = {
  id: string;
  assignmentId: string;
  subjectId: string;
  submissionNo?: number;
  bodyText?: string;
  markingStatus?: string;
};

export default function MarkingQueuePage() {
  const { data } = useFundoMarkingQueue();
  const mark = useMarkSubmission();
  const [scores, setScores] = useState<Record<string, string>>({});
  const [feedback, setFeedback] = useState<Record<string, string>>({});

  const queue = (((data?.data as { items?: Submission[] } | undefined)?.items) ?? []);

  async function onMark(s: Submission, passed: boolean) {
    await mark.mutateAsync({
      submissionId: s.id,
      body: {
        score: scores[s.id] ? Number(scores[s.id]) : undefined,
        passed,
        feedbackText: feedback[s.id] ?? "",
      },
    });
  }

  return (
    <PageShell title="Marking queue" subtitle="Submissions awaiting your review. Score, give feedback, and pass or return.">
      {queue.length === 0 && <p className="text-sm text-muted-foreground">Nothing awaiting marking.</p>}
      <ul className="space-y-3" data-testid="marking-queue">
        {queue.map((s) => (
          <li key={s.id} className="rounded-lg border border-border bg-card p-4">
            <div className="flex items-center justify-between">
              <p className="text-sm font-medium text-foreground">{s.subjectId} · attempt {s.submissionNo ?? 1}</p>
              <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-800">{s.markingStatus ?? "PENDING_MARK"}</span>
            </div>
            {s.bodyText && <p className="mt-1 whitespace-pre-wrap text-sm text-muted-foreground">{s.bodyText}</p>}
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <input
                type="number"
                placeholder="Score"
                value={scores[s.id] ?? ""}
                onChange={(e) => setScores((m) => ({ ...m, [s.id]: e.target.value }))}
                className="w-24 rounded border border-border px-2 py-1 text-sm"
              />
              <input
                placeholder="Feedback"
                value={feedback[s.id] ?? ""}
                onChange={(e) => setFeedback((m) => ({ ...m, [s.id]: e.target.value }))}
                className="flex-1 rounded border border-border px-2 py-1 text-sm"
              />
              <button onClick={() => onMark(s, true)} disabled={mark.isPending} className="rounded bg-emerald-600 px-3 py-1 text-sm text-white disabled:opacity-60">Pass</button>
              <button onClick={() => onMark(s, false)} disabled={mark.isPending} className="rounded bg-rose-600 px-3 py-1 text-sm text-white disabled:opacity-60">Fail</button>
            </div>
          </li>
        ))}
      </ul>
    </PageShell>
  );
}
