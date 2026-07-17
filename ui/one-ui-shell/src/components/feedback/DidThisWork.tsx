"use client";

/**
 * "Did this work?" — a compact, reusable feedback prompt for public confirmation/result
 * surfaces. A Yes closes the loop with a thank-you; a Partly / No opens a short, optional
 * note and posts to the existing anonymous public feedback lane
 * ({@code /internal/v1/public/gateway/feedback}), returning the real claim code so the
 * citizen can track the outcome.
 *
 * Safety: the only auto-context attached is the page path and journey stage — NEVER any
 * clinical data, credential, Health ID or account identity. On these public pages no such
 * data exists, and we still keep the payload deliberately minimal. The feedback lane accepts
 * caseType COMPLAINT | SAFETY_CONCERN; a "did this work" signal maps to COMPLAINT (a service
 * experience that could be better), which routes to the quality/feedback (Rito) case flow.
 */

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { classifyRouteJourney } from "@/lib/ui-route-journey-map";
import { apiClient } from "@/lib/api-client";

interface FeedbackReceipt {
  caseReference?: string;
  claimCode?: string;
  status?: string;
}

type Answer = "yes" | "partly" | "no";

function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.status === 429) return "Thanks — but too many messages from this connection right now. Please try later.";
    if (obj.error?.message) return obj.error.message;
  }
  return "We could not send that just now. Please try again later.";
}

export interface DidThisWorkProps {
  /** What the citizen just did, used only to label the feedback title (no PII). */
  surfaceLabel: string;
  className?: string;
}

export function DidThisWork({ surfaceLabel, className }: DidThisWorkProps) {
  const pathname = usePathname() || "/";
  const [answer, setAnswer] = useState<Answer | null>(null);
  const [note, setNote] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<FeedbackReceipt | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const journey = classifyRouteJourney(pathname);
      const safeContext = `Page: ${pathname} · Journey: ${journey} · Answer: ${answer}`;
      const description = note.trim()
        ? `${note.trim()}\n\n(${safeContext})`
        : `A citizen indicated this did not fully work.\n\n(${safeContext})`;
      const res = await apiClient.post<FeedbackReceipt>("/internal/v1/public/gateway/feedback", {
        caseType: "COMPLAINT",
        title: `Did this work — ${surfaceLabel}`,
        description,
      });
      setReceipt(res);
    } catch (e2) {
      setError(errMessage(e2));
    } finally {
      setSubmitting(false);
    }
  };

  const wrap = `rounded-xl border border-slate-200 bg-white p-4 ${className ?? ""}`;

  if (receipt) {
    return (
      <div className={wrap} data-testid="did-this-work-receipt">
        <p className="text-sm font-semibold text-slate-900">Thank you — your feedback was received.</p>
        <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 p-3">
          <p className="text-xs uppercase tracking-wide text-slate-500">Your claim code</p>
          <p className="mt-1 font-mono text-lg font-bold tracking-wider text-slate-900">
            {receipt.claimCode}
          </p>
          <p className="mt-1 text-xs text-slate-600">
            Keep this code to check the outcome. Reference:{" "}
            <span className="font-mono">{receipt.caseReference}</span>
          </p>
        </div>
        <Link
          href="/welcome/report/status"
          className="mt-3 inline-block text-sm font-medium text-emerald-700 hover:text-emerald-800"
        >
          Check a report&apos;s status →
        </Link>
      </div>
    );
  }

  if (answer === "yes") {
    return (
      <div className={wrap} data-testid="did-this-work-thanks">
        <p className="text-sm font-semibold text-emerald-800">Great — thanks for letting us know.</p>
      </div>
    );
  }

  return (
    <div className={wrap} data-testid="did-this-work">
      <p className="text-sm font-medium text-slate-800">Did this work for you?</p>
      {answer === null ? (
        <div className="mt-2 flex flex-wrap gap-2">
          {(["yes", "partly", "no"] as const).map((value) => (
            <button
              key={value}
              type="button"
              onClick={() => setAnswer(value)}
              className="rounded-lg border border-slate-300 bg-white px-4 py-1.5 text-sm font-medium text-slate-700 hover:border-emerald-300 hover:bg-emerald-50"
            >
              {value === "yes" ? "Yes" : value === "partly" ? "Partly" : "No"}
            </button>
          ))}
        </div>
      ) : (
        <form onSubmit={submit} className="mt-3 space-y-3">
          {error && (
            <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
              {error}
            </div>
          )}
          <label className="block text-sm text-slate-700">
            What could be better? (optional)
            <textarea
              value={note}
              onChange={(e) => setNote(e.target.value)}
              rows={3}
              maxLength={4000}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              placeholder="Tell us what happened — do not include any personal or health details."
            />
          </label>
          <p className="text-xs text-slate-500">
            Your message is anonymous. We only attach the page you were on — never personal or
            health information.
          </p>
          <div className="flex gap-2">
            <button
              type="submit"
              disabled={submitting}
              className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-50"
            >
              {submitting ? "Sending…" : "Send feedback"}
            </button>
            <button
              type="button"
              onClick={() => setAnswer(null)}
              className="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100"
            >
              Cancel
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
