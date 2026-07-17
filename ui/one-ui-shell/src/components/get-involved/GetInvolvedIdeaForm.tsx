"use client";

/**
 * Anonymous "Get Involved" idea / experience submission (gateway ADR W4
 * gateway-get-involved-claim). Need-first and lightweight: need area → what could be better →
 * a better experience → who it helps → willing to help. No account required; on success we
 * show the ONE-TIME claim code (reusing the report claim-code receipt pattern) which is the
 * only key to check the outcome later.
 *
 * This is the "something could be better" surface — distinct from the "something went wrong"
 * report lane. Honest failures: a rate-limit or outage never pretends the idea was captured.
 *
 * Backend: POST /internal/v1/public/gateway/get-involved/contributions
 *   body { type, needArea, title, problemOrOpportunity, betterExperience, beneficiary,
 *          willingToHelp, contact }
 *   -> { reference, claimCode, status, message }
 */

import { useState } from "react";
import Link from "next/link";
import { apiClient } from "@/lib/api-client";
import { DidThisWork } from "@/components/feedback/DidThisWork";

interface ContributionReceipt {
  reference?: string;
  claimCode?: string;
  status?: string;
  message?: string;
}

const NEED_AREAS = [
  "Finding & getting care",
  "Booking & appointments",
  "Payments & health cover",
  "Medicines & pharmacy",
  "My records & results",
  "Emergency & ambulance",
  "Wellness, diet & fitness",
  "Accessibility & language",
  "Something else",
] as const;

function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.status === 429) return "Too many submissions from this connection. Please try again later.";
    if (obj.error?.message) return obj.error.message;
  }
  return "We could not capture your idea just now. Please try again later or sign in to submit.";
}

export function GetInvolvedIdeaForm({ defaultType = "IDEA" }: { defaultType?: "IDEA" | "EXPERIENCE" }) {
  const [type, setType] = useState<"IDEA" | "EXPERIENCE">(defaultType);
  const [needArea, setNeedArea] = useState<string>(NEED_AREAS[0]);
  const [title, setTitle] = useState("");
  const [problem, setProblem] = useState("");
  const [better, setBetter] = useState("");
  const [beneficiary, setBeneficiary] = useState("");
  const [willingToHelp, setWillingToHelp] = useState(false);
  const [contact, setContact] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<ContributionReceipt | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const res = await apiClient.post<ContributionReceipt>(
        "/internal/v1/public/gateway/get-involved/contributions",
        {
          type,
          needArea,
          title: title.trim() || undefined,
          problemOrOpportunity: problem.trim() || undefined,
          betterExperience: better.trim() || undefined,
          beneficiary: beneficiary.trim() || undefined,
          willingToHelp,
          contact: willingToHelp ? contact.trim() || undefined : undefined,
        },
      );
      setReceipt(res);
    } catch (e2) {
      setError(errMessage(e2));
    } finally {
      setSubmitting(false);
    }
  };

  if (receipt) {
    return (
      <div className="rounded-2xl border border-emerald-300 bg-emerald-50 p-6" data-testid="get-involved-receipt">
        <h2 className="font-semibold text-emerald-900">Thank you — your idea has been received</h2>
        {receipt.reference && (
          <p className="mt-2 text-sm text-emerald-800">
            Reference: <span className="font-mono font-semibold">{receipt.reference}</span>
          </p>
        )}
        <div className="mt-4 rounded-lg border border-emerald-200 bg-white p-4">
          <p className="text-xs uppercase tracking-wide text-slate-500">Your claim code</p>
          <p className="mt-1 font-mono text-2xl font-bold tracking-wider text-slate-900">
            {receipt.claimCode}
          </p>
          <p className="mt-2 text-sm text-red-700">
            Write this code down now. It is the only way to check what happens to your idea, and it
            will not be shown again.
          </p>
        </div>
        <div className="mt-4 flex flex-wrap gap-4">
          <Link
            href="/get-involved/track"
            className="text-sm font-medium text-emerald-700 hover:text-emerald-800"
          >
            Track your idea →
          </Link>
          <Link
            href="/get-involved/board"
            className="text-sm font-medium text-emerald-700 hover:text-emerald-800"
          >
            See ideas Zimbabwe is exploring →
          </Link>
        </div>
        <div className="mt-5">
          <DidThisWork surfaceLabel="Sharing an idea" />
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={submit} className="space-y-4" data-testid="get-involved-idea-form">
      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</div>
      )}

      <div>
        <span className="mb-1 block text-sm font-medium text-slate-800">What are you sharing?</span>
        <div className="flex flex-wrap gap-2">
          {(["IDEA", "EXPERIENCE"] as const).map((value) => (
            <button
              key={value}
              type="button"
              onClick={() => setType(value)}
              className={`rounded-lg border px-4 py-1.5 text-sm font-medium ${
                type === value
                  ? "border-emerald-400 bg-emerald-50 text-emerald-800"
                  : "border-slate-300 bg-white text-slate-700 hover:bg-slate-50"
              }`}
            >
              {value === "IDEA" ? "An idea for something better" : "An experience I had"}
            </button>
          ))}
        </div>
      </div>

      <div>
        <label className="mb-1 block text-sm font-medium text-slate-800">
          Which part of your health experience is this about?
        </label>
        <select
          value={needArea}
          onChange={(e) => setNeedArea(e.target.value)}
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
        >
          {NEED_AREAS.map((area) => (
            <option key={area} value={area}>
              {area}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label className="mb-1 block text-sm font-medium text-slate-800">
          A short title <span className="font-normal text-slate-400">(optional)</span>
        </label>
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={512}
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
          placeholder="Sum it up in a few words"
        />
      </div>

      <div>
        <label className="mb-1 block text-sm font-medium text-slate-800">
          What is the problem or opportunity?
        </label>
        <textarea
          value={problem}
          onChange={(e) => setProblem(e.target.value)}
          rows={4}
          maxLength={8000}
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
          placeholder="What could be better, or what did you notice?"
        />
      </div>

      <div>
        <label className="mb-1 block text-sm font-medium text-slate-800">
          What would a better experience look like? <span className="font-normal text-slate-400">(optional)</span>
        </label>
        <textarea
          value={better}
          onChange={(e) => setBetter(e.target.value)}
          rows={3}
          maxLength={8000}
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
        />
      </div>

      <div>
        <label className="mb-1 block text-sm font-medium text-slate-800">
          Who would this help most? <span className="font-normal text-slate-400">(optional)</span>
        </label>
        <input
          value={beneficiary}
          onChange={(e) => setBeneficiary(e.target.value)}
          maxLength={255}
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
          placeholder="e.g. mothers with young children, rural patients"
        />
      </div>

      <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
        <label className="flex items-start gap-2 text-sm text-slate-800">
          <input
            type="checkbox"
            checked={willingToHelp}
            onChange={(e) => setWillingToHelp(e.target.checked)}
            className="mt-0.5"
          />
          <span>I&apos;m willing to help shape this (e.g. answer a question or test it).</span>
        </label>
        {willingToHelp && (
          <div className="mt-3">
            <label className="mb-1 block text-sm font-medium text-slate-800">
              A contact so we can reach you <span className="font-normal text-slate-400">(optional)</span>
            </label>
            <input
              value={contact}
              onChange={(e) => setContact(e.target.value)}
              maxLength={255}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              placeholder="Phone or email — only if you want to be contacted"
            />
          </div>
        )}
      </div>

      <p className="text-xs text-slate-500">
        Your submission is anonymous. Please do not include personal or health details. You&apos;ll
        get a one-time claim code to follow what happens next.
      </p>

      <button
        type="submit"
        disabled={submitting || (!title.trim() && !problem.trim())}
        className="rounded-lg bg-emerald-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-50"
      >
        {submitting ? "Submitting…" : "Share this idea"}
      </button>
    </form>
  );
}
