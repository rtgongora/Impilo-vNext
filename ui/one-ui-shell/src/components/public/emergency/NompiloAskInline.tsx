"use client";

/**
 * Inline single-turn "Ask Nompilo" for the public emergency journey.
 *
 * Free-text companion to the deterministic triage: the protocol decides
 * escalation; this answers side questions ("what do I do while I wait?",
 * "is paracetamol safe in pregnancy?") through the anonymous grounded lane
 * POST /internal/v1/public/gateway/guidance/ask.
 *
 * Honesty rules surfaced in the UI: the backend labels every answer
 * (llm / retrieval / none); a "none" answer is shown as an honest
 * "I don't know — ask a health worker", and every answer carries the
 * not-a-diagnosis disclaimer. Degraded/unavailable is stated plainly and
 * never blocks the surrounding journey.
 */

import { useState } from "react";
import { Loader2, Send, Sparkles } from "lucide-react";
import { apiClient } from "@/lib/api-client";

interface AskAnswer {
  answer?: string;
  answerSource?: string;
  confidence?: number;
  sources?: string[];
  disclaimer?: string;
}

export function NompiloAskInline({ className = "" }: { className?: string }) {
  const [question, setQuestion] = useState("");
  const [askedQuestion, setAskedQuestion] = useState("");
  const [answer, setAnswer] = useState<AskAnswer | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function ask() {
    const q = question.trim();
    if (q.length < 3 || loading) return;
    setLoading(true);
    setError("");
    setAnswer(null);
    setAskedQuestion(q);
    try {
      const res = await apiClient.post<AskAnswer>("/internal/v1/public/gateway/guidance/ask", {
        question: q,
      });
      setAnswer(res);
      setQuestion("");
    } catch (e) {
      const status = (e as { status?: number })?.status;
      if (status === 429) {
        setError("Nompilo is very busy — please wait a moment and try again.");
      } else {
        setError(
          "Nompilo is unavailable right now. For urgent questions, call the numbers above or ask a health worker.",
        );
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div
      data-testid="nompilo-ask"
      className={`rounded-xl border border-violet-200 bg-violet-50/50 p-3.5 ${className}`}
    >
      <p className="flex items-center gap-1.5 text-[13px] font-semibold text-violet-900">
        <Sparkles className="h-4 w-4 text-violet-600" aria-hidden />
        Ask Nompilo a question
      </p>
      <div className="mt-2 flex gap-2">
        <label htmlFor="nompilo-ask-input" className="sr-only">
          Your question for Nompilo
        </label>
        <input
          id="nompilo-ask-input"
          data-testid="nompilo-ask-input"
          type="text"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && ask()}
          maxLength={500}
          placeholder="e.g. What should I do while we wait for help?"
          className="min-h-[44px] min-w-0 flex-1 rounded-lg border border-slate-300 bg-white px-3 py-2 text-[14px] focus:border-violet-500 focus:outline-none"
        />
        <button
          type="button"
          data-testid="nompilo-ask-send"
          onClick={ask}
          disabled={loading || question.trim().length < 3}
          aria-label="Ask Nompilo"
          className="grid h-11 w-11 shrink-0 place-items-center rounded-lg bg-violet-600 text-white transition hover:bg-violet-700 disabled:opacity-50 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-600"
        >
          {loading ? (
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
          ) : (
            <Send className="h-4 w-4" aria-hidden />
          )}
        </button>
      </div>

      {error && (
        <p data-testid="nompilo-ask-error" role="alert" className="mt-2 text-sm text-red-700">
          {error}
        </p>
      )}

      {answer && (
        <div data-testid="nompilo-ask-answer" className="mt-3 rounded-lg bg-white p-3 ring-1 ring-violet-100">
          <p className="text-[12.5px] font-medium text-slate-400">You asked: {askedQuestion}</p>
          {answer.answerSource === "none" ? (
            <p className="mt-1.5 text-sm leading-relaxed text-slate-700">
              {answer.answer ||
                "I don't have trusted information about that. Please ask a health worker."}
            </p>
          ) : (
            <p className="mt-1.5 whitespace-pre-line text-sm leading-relaxed text-slate-800">
              {answer.answer}
            </p>
          )}
          {answer.sources && answer.sources.length > 0 && (
            <p className="mt-2 text-[12px] text-slate-500">
              Based on: {answer.sources.slice(0, 3).join(" · ")}
            </p>
          )}
          <p className="mt-2 border-t border-slate-100 pt-2 text-[11.5px] leading-snug text-slate-400">
            {answer.disclaimer ||
              "Nompilo gives general guidance, not a diagnosis. In an emergency, call the numbers shown."}
          </p>
        </div>
      )}
    </div>
  );
}
