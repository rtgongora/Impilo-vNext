"use client";

/**
 * Post-visit rating page (RW12). Closes the in-app feedback link dispatched by Khuluma after
 * a PCT-verified encounter: the patient rates their experience of the visit. Rito resolves the
 * attributed provider from the encounter reference and stamps the rating verified.
 *
 * Only the two *experience* domains are captured here (CLIENT_EXPERIENCE, ACCESS_PROCESS) — the
 * doctrine's public, citizen-voiced domains. Professional-quality and safety domains come from
 * peer/clinical review, never a citizen form. A rating never changes a provider's licence,
 * scope or access (regulation firewall) — this page only records experience feedback.
 *
 * Route: /feedback/visit/[encounterRef]?provider=<name>&facility=<name>
 */

import { useState } from "react";
import { useParams, useSearchParams } from "next/navigation";
import Link from "next/link";
import { CheckCircle2, Loader2, ShieldCheck, Star } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface CreatedRating {
  id?: string;
  verifiedInteraction?: boolean;
  moderationState?: string;
}

function unwrap<T>(payload: unknown): T | null {
  if (payload && typeof payload === "object" && "data" in payload) {
    const inner = (payload as { data?: unknown }).data;
    if (inner && typeof inner === "object") return inner as T;
  }
  return (payload as T) ?? null;
}

function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status === 409) return "You have already rated this visit. Thank you.";
    if (obj.status) return `Could not submit your rating (HTTP ${obj.status}).`;
  }
  return "Could not submit your rating. Please try again.";
}

function StarRow({
  label,
  hint,
  value,
  onChange,
}: {
  label: string;
  hint: string;
  value: number;
  onChange: (n: number) => void;
}) {
  return (
    <div>
      <label className="mb-0.5 block text-sm font-medium text-foreground">{label}</label>
      <p className="mb-1.5 text-xs text-muted-foreground">{hint}</p>
      <div className="flex gap-1" role="radiogroup" aria-label={label}>
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            key={n}
            type="button"
            role="radio"
            aria-checked={value === n}
            aria-label={`${n} star${n === 1 ? "" : "s"}`}
            onClick={() => onChange(n)}
            className="rounded p-0.5 transition hover:scale-110 focus:outline-none focus-visible:ring-2 focus-visible:ring-teal-500"
          >
            <Star
              className={`h-7 w-7 ${
                n <= value ? "fill-amber-400 text-amber-400" : "text-muted-foreground/40"
              }`}
            />
          </button>
        ))}
      </div>
    </div>
  );
}

export default function PostVisitRatingPage() {
  const params = useParams<{ encounterRef: string }>();
  const encounterRef = decodeURIComponent(String(params?.encounterRef ?? ""));
  const searchParams = useSearchParams();
  const providerName = searchParams.get("provider");
  const facilityName = searchParams.get("facility");

  const [overall, setOverall] = useState(0);
  const [experience, setExperience] = useState(0);
  const [access, setAccess] = useState(0);
  const [comment, setComment] = useState("");

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<CreatedRating | null>(null);

  const canSubmit = overall > 0 && !submitting;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      const domainScores: Array<Record<string, unknown>> = [];
      if (experience > 0) {
        domainScores.push({
          domain: "CLIENT_EXPERIENCE",
          measure: "overall",
          score: experience,
          scale: "1-5",
        });
      }
      if (access > 0) {
        domainScores.push({ domain: "ACCESS_PROCESS", measure: "overall", score: access, scale: "1-5" });
      }
      const body: Record<string, unknown> = {
        encounterRef,
        overallScore: overall,
        respondentClass: "CLIENT",
        respondentIdentityProtected: true,
        domainScores,
      };
      if (comment.trim()) body.narrativeText = comment.trim();
      const res = await apiClient.post<unknown>("/internal/v1/nompilo/rito/ratings", body);
      setCreated(unwrap<CreatedRating>(res));
    } catch (e2) {
      setError(errMessage(e2));
    } finally {
      setSubmitting(false);
    }
  };

  const subject = providerName ?? "your care provider";

  return (
    <AppLayout>
      <PageShell
        title="How was your visit?"
        subtitle={
          facilityName
            ? `Your experience with ${subject} at ${facilityName}`
            : `Your experience with ${subject}`
        }
        serviceSlug="rito"
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
          {created ? (
            <div className="rounded-2xl border border-teal-300 bg-teal-50 p-6">
              <div className="mb-2 flex items-center gap-2 font-semibold text-teal-900">
                <CheckCircle2 className="h-5 w-5" /> Thank you — your feedback was recorded
              </div>
              <p className="text-sm text-teal-800">
                {created.verifiedInteraction
                  ? "Because this was a confirmed visit, your rating is counted as verified experience."
                  : "Your feedback has been received and will be reviewed."}
              </p>
              <Link
                href="/my-life"
                className="mt-4 inline-flex rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700"
              >
                Done
              </Link>
            </div>
          ) : !encounterRef ? (
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
              This feedback link is missing its visit reference. Please open the link from your
              message again.
            </div>
          ) : (
            <form onSubmit={submit} className="max-w-xl space-y-5">
              {error && (
                <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                  {error}
                </div>
              )}

              <StarRow
                label="Overall, how was your visit?"
                hint="Your general impression of the care you received."
                value={overall}
                onChange={setOverall}
              />
              <StarRow
                label="Care experience"
                hint="How you were treated — respect, listening, communication, dignity."
                value={experience}
                onChange={setExperience}
              />
              <StarRow
                label="Access & process"
                hint="Getting seen — waiting time, ease of the process, information given."
                value={access}
                onChange={setAccess}
              />

              <div>
                <label className="mb-1 block text-sm font-medium text-foreground">
                  Anything else? (optional)
                </label>
                <textarea
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  rows={4}
                  placeholder="What went well, or what could be better?"
                  className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                />
              </div>

              <div className="flex items-start gap-2 rounded-lg border border-border bg-muted/40 p-3 text-xs text-muted-foreground">
                <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-teal-600" />
                <span>
                  Your identity is protected — the provider never sees who left this rating. Feedback
                  helps improve care and is never used, on its own, to change a provider&apos;s
                  registration.
                </span>
              </div>

              <button
                type="submit"
                disabled={!canSubmit}
                className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50"
              >
                {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
                {submitting ? "Submitting…" : "Submit rating"}
              </button>
            </form>
          )}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
