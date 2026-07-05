"use client";

import { useState } from "react";
import { BarChart2 } from "lucide-react";
import { useLiveActivatePoll, useLiveCreatePoll, useLivePolls } from "@/hooks/queries/useLive";

/**
 * Class quiz launcher — facilitator-side poll control for the classroom,
 * REUSING the Impilo Live poll surface of the linked live event (no parallel
 * quiz store). Launch = create + activate; learners answer through the same
 * live poll respond path.
 *
 * HONEST SCOPE: the live poll list payload carries question/options/status
 * only — per-option response counts are not exposed on this surface (they
 * live in the event analytics dashboard).
 */
export function ClassQuizLauncherPanel({
  eventId,
  participantId,
}: {
  eventId: string;
  participantId: string;
}) {
  const { data: polls = [] } = useLivePolls(eventId);
  const createPoll = useLiveCreatePoll();
  const activatePoll = useLiveActivatePoll();
  const [question, setQuestion] = useState("");
  const [optionsDraft, setOptionsDraft] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function onLaunch() {
    setError(null);
    const options = optionsDraft
      .split(/\r?\n|,/) // one per line, commas tolerated
      .map((o) => o.trim())
      .filter(Boolean);
    if (!question.trim() || options.length < 2) {
      setError("A quiz needs a question and at least two options.");
      return;
    }
    try {
      const created = (await createPoll.mutateAsync({
        eventId,
        body: { question: question.trim(), options, createdBy: participantId },
      })) as { id?: string } | undefined;
      if (created?.id) {
        await activatePoll.mutateAsync({ eventId, pollId: created.id });
      }
      setQuestion("");
      setOptionsDraft("");
    } catch {
      setError("Could not launch the quiz.");
    }
  }

  return (
    <div data-testid="quiz-launcher" className="space-y-3">
      <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        <BarChart2 className="h-3.5 w-3.5" /> Class quiz / poll
      </p>
      <div className="space-y-2 rounded border border-border bg-background p-2">
        <input
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="Quiz question…"
          data-testid="quiz-question-input"
          className="w-full rounded border border-border px-2 py-1.5 text-xs"
        />
        <textarea
          value={optionsDraft}
          onChange={(e) => setOptionsDraft(e.target.value)}
          placeholder={"Options — one per line"}
          rows={3}
          data-testid="quiz-options-input"
          className="w-full rounded border border-border px-2 py-1.5 text-xs"
        />
        <button
          type="button"
          onClick={() => void onLaunch()}
          disabled={createPoll.isPending || activatePoll.isPending}
          data-testid="quiz-launch-button"
          className="rounded bg-violet-700 px-2.5 py-1.5 text-xs font-medium text-white disabled:opacity-60"
        >
          {createPoll.isPending || activatePoll.isPending ? "Launching…" : "Launch quiz"}
        </button>
        {error ? <p className="text-xs text-warning-foreground">{error}</p> : null}
      </div>

      <div>
        <p className="mb-1 text-[11px] font-medium uppercase text-muted-foreground">Launched ({polls.length})</p>
        {polls.length === 0 ? (
          <p className="text-xs text-muted-foreground">No quizzes launched yet.</p>
        ) : (
          <ul className="space-y-1">
            {polls.map((poll) => (
              <li key={poll.id} data-testid="quiz-entry" className="rounded border border-border px-2 py-1.5 text-xs">
                <div className="flex items-center justify-between gap-2">
                  <span className="min-w-0 truncate font-medium text-foreground">{poll.question}</span>
                  <span className="flex-shrink-0 rounded bg-neutral-100 px-1.5 py-0.5 text-[10px] uppercase text-muted-foreground">
                    {poll.status}
                  </span>
                </div>
                {poll.status !== "ACTIVE" ? (
                  <button
                    type="button"
                    onClick={() => activatePoll.mutate({ eventId, pollId: poll.id })}
                    className="mt-1 rounded border border-border px-2 py-0.5 text-[11px] text-foreground hover:bg-neutral-100"
                  >
                    Activate
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
        )}
        <p className="mt-1 text-[10px] text-muted-foreground">
          Response counts are on the event analytics dashboard, not this list.
        </p>
      </div>
    </div>
  );
}
