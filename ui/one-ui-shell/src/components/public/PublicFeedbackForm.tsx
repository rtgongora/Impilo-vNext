"use client";

/**
 * Anonymous public feedback/safety-concern form (gateway ADR W4 gateway-feedback-claim).
 * Posts to the public gateway lane without an account; on success shows the ONE-TIME
 * claim code the person needs to check the outcome. Honest failure states — a rate-limit
 * or outage message never pretends the report was captured.
 */

import { useState } from "react";
import Link from "next/link";
import { apiClient } from "@/lib/api-client";

interface FeedbackReceipt {
  caseReference?: string;
  claimCode?: string;
  status?: string;
  message?: string;
}

const CASE_TYPES = [
  { value: "SAFETY_CONCERN", label: "Unsafe care or a safety concern" },
  { value: "COMPLAINT", label: "A complaint about a facility or service" },
] as const;

function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status === 429) return "Too many reports from this connection. Please try again later.";
  }
  return "We could not capture your report just now. Please try again later or sign in to submit.";
}

export function PublicFeedbackForm() {
  const [caseType, setCaseType] = useState<string>("SAFETY_CONCERN");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [contact, setContact] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<FeedbackReceipt | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const res = await apiClient.post<FeedbackReceipt>("/internal/v1/public/gateway/feedback", {
        caseType,
        title,
        description,
        contact: contact.trim() || undefined,
      });
      setReceipt(res);
    } catch (e2) {
      setError(errMessage(e2));
    } finally {
      setSubmitting(false);
    }
  };

  if (receipt) {
    return (
      <div
        className="rounded-2xl border border-emerald-300 bg-emerald-50 p-6"
        data-testid="feedback-receipt"
      >
        <h2 className="font-semibold text-emerald-900">Your report has been received</h2>
        <p className="mt-2 text-sm text-emerald-800">
          Reference: <span className="font-mono font-semibold">{receipt.caseReference}</span>
        </p>
        <div className="mt-4 rounded-lg border border-emerald-200 bg-white p-4">
          <p className="text-xs uppercase tracking-wide text-slate-500">Your claim code</p>
          <p className="mt-1 font-mono text-2xl font-bold tracking-wider text-slate-900">
            {receipt.claimCode}
          </p>
          <p className="mt-2 text-sm text-red-700">
            Write this code down now. It is the only way to check the outcome of your report,
            and it will not be shown again.
          </p>
        </div>
        <Link
          href="/welcome/report/status"
          className="mt-4 inline-block text-sm font-medium text-emerald-700 hover:text-emerald-800"
        >
          Check a report&apos;s status →
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={submit} className="space-y-4" data-testid="public-feedback-form">
      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
          {error}
        </div>
      )}
      <div>
        <label className="mb-1 block text-sm font-medium text-slate-800">
          What are you reporting?
        </label>
        <select
          value={caseType}
          onChange={(e) => setCaseType(e.target.value)}
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
        >
          {CASE_TYPES.map((t) => (
            <option key={t.value} value={t.value}>
              {t.label}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="mb-1 block text-sm font-medium text-slate-800">Short title</label>
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
          maxLength={200}
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
        />
      </div>
      <div>
        <label className="mb-1 block text-sm font-medium text-slate-800">What happened?</label>
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          required
          rows={5}
          maxLength={4000}
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
        />
      </div>
      <div>
        <label className="mb-1 block text-sm font-medium text-slate-800">
          Contact (optional)
        </label>
        <input
          value={contact}
          onChange={(e) => setContact(e.target.value)}
          maxLength={255}
          placeholder="Phone or email, only if you want to be contacted"
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
        />
        <p className="mt-1 text-xs text-slate-500">
          Your report is anonymous. If you leave a contact it is kept with the case notes only.
        </p>
      </div>
      <button
        type="submit"
        disabled={submitting || !title || !description}
        className="rounded-lg bg-emerald-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-50"
      >
        {submitting ? "Submitting…" : "Submit anonymous report"}
      </button>
    </form>
  );
}
