"use client";

/**
 * Respectful maternity care experience survey (RMNP W12).
 *
 * Fetches the governed measure set from the BFF and renders it — the prompts, the scale
 * captions and the reverse-scored polarity all come from the server. This page never carries a
 * hardcoded copy of the questions: if the instrument cannot be fetched, it shows an honest
 * "unavailable" state rather than falling back to a stale local copy (docs/frontend/GAP_CLOSURE_RULES.md).
 *
 * Anonymous by default: `identifyMe` is an explicit opt-in. Raw answers only — reverse-scoring is
 * CKP's job, never this client's.
 *
 * Route: /my-life/feedback/respectful-maternity
 */

import { useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  Copy,
  Loader2,
  RefreshCw,
  ShieldCheck,
} from "lucide-react";
import { GlassSurface, LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useRespectfulMaternityCare,
  type RespectfulCareMeasure,
  type RespectfulCareScale,
} from "@/hooks/useRespectfulMaternityCare";

function LikertRow({
  measure,
  scale,
  value,
  onChange,
}: {
  measure: RespectfulCareMeasure;
  scale: RespectfulCareScale;
  value: number | undefined;
  onChange: (n: number) => void;
}) {
  const options: number[] = [];
  for (let n = scale.low; n <= scale.high; n += 1) options.push(n);

  return (
    <div className="rounded-xl border border-border bg-background/60 p-4">
      <label className="mb-2 block text-sm font-medium text-foreground">{measure.prompt}</label>
      <div className="flex flex-wrap items-center gap-2" role="radiogroup" aria-label={measure.prompt}>
        {options.map((n) => (
          <button
            key={n}
            type="button"
            role="radio"
            aria-checked={value === n}
            aria-label={`${n}`}
            onClick={() => onChange(n)}
            data-testid={`rmc-answer-${measure.measure}-${n}`}
            className={`h-9 w-9 rounded-full border text-sm font-semibold transition ${
              value === n
                ? "border-teal-600 bg-teal-600 text-white"
                : "border-border bg-background text-muted-foreground hover:border-teal-400"
            }`}
          >
            {n}
          </button>
        ))}
      </div>
      <div className="mt-1.5 flex justify-between text-[11px] text-muted-foreground">
        <span className="capitalize">{scale.low} = {scale.lowMeans}</span>
        <span className="capitalize">{scale.high} = {scale.highMeans}</span>
      </div>
    </div>
  );
}

export default function RespectfulMaternityCarePage() {
  const searchParams = useSearchParams();
  // Optional deep-link context, e.g. from a post-visit message — same idea as
  // /feedback/visit/[encounterRef]. Absent is fine: the narrative still files anonymously, and
  // the BFF is honest when scores cannot be attributed without one of these.
  const encounterRef = searchParams.get("encounterRef") ?? undefined;
  const providerPublicId = searchParams.get("provider") ?? undefined;
  const facilityFromQuery = searchParams.get("facility") ?? "";

  const {
    instrument,
    loading,
    instrumentUnavailable,
    refetch,
    submitting,
    submitError,
    result,
    submit,
  } = useRespectfulMaternityCare();

  const [answers, setAnswers] = useState<Record<string, number>>({});
  const [narrative, setNarrative] = useState("");
  const [identifyMe, setIdentifyMe] = useState(false);
  const [facilityId, setFacilityId] = useState(facilityFromQuery);
  const [copied, setCopied] = useState(false);

  const answeredCount = Object.keys(answers).length;
  const canSubmit = (answeredCount > 0 || narrative.trim().length > 0) && !submitting;

  const setAnswer = (measure: string, n: number) => {
    setAnswers((a) => ({ ...a, [measure]: n }));
  };

  const reportingPeriod = useMemo(() => new Date().toISOString().slice(0, 7), []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    await submit({
      facilityId: facilityId.trim() || undefined,
      encounterRef,
      providerPublicId,
      reportingPeriod,
      answers: answeredCount > 0 ? answers : undefined,
      narrative: narrative.trim() || undefined,
      identifyMe,
    });
  };

  const copyClaimCode = async (code: string) => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), 2500);
    } catch {
      // Clipboard access can be denied; the code is still shown on screen to copy manually.
    }
  };

  return (
    <AppLayout>
      <PageShell
        title="Respectful maternity care"
        subtitle="Tell us how you were treated during labour, birth or right after — anonymously by default"
        serviceSlug="rito"
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
          <div className="mb-2">
            <Link
              href="/my-life/feedback"
              className="inline-flex items-center gap-1 text-xs font-semibold text-teal-700 hover:underline"
            >
              <ArrowLeft className="h-3.5 w-3.5" /> Back to feedback
            </Link>
          </div>

          {loading ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground" data-testid="rmc-loading">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading the questions…
            </div>
          ) : instrumentUnavailable ? (
            <div
              className="rounded-2xl border border-amber-300 bg-amber-50 p-6"
              data-testid="rmc-instrument-unavailable"
            >
              <div className="mb-2 flex items-center gap-2 font-semibold text-amber-900">
                <AlertTriangle className="h-5 w-5" /> The questions could not be loaded
              </div>
              <p className="text-sm text-amber-800">
                We could not retrieve the respectful maternity care questions right now. We do not
                show a stored copy of them, because which answer counts as good or bad is decided
                by the current version — showing an old copy could score your answers backwards.
                Please try again in a moment.
              </p>
              <button
                type="button"
                onClick={refetch}
                className="mt-4 inline-flex items-center gap-2 rounded-lg bg-amber-600 px-4 py-2 text-sm font-medium text-white hover:bg-amber-700"
                data-testid="rmc-retry"
              >
                <RefreshCw className="h-4 w-4" /> Try again
              </button>
            </div>
          ) : result ? (
            <div className="space-y-4" data-testid="rmc-result">
              {result.narrative?.filed && result.narrative.claimCode ? (
                <div className="rounded-2xl border-2 border-teal-400 bg-teal-50 p-6">
                  <div className="mb-2 flex items-center gap-2 font-semibold text-teal-900">
                    <CheckCircle2 className="h-5 w-5" /> Your report was filed
                  </div>
                  <p className="mb-3 text-sm text-teal-800">
                    Save this claim code somewhere safe now — it is shown only once and cannot be
                    recovered later. It is the only way to check on your report without giving your
                    identity.
                  </p>
                  <div className="flex flex-wrap items-center gap-2 rounded-xl border border-teal-300 bg-white p-3">
                    <span
                      className="flex-1 break-all font-mono text-lg font-bold tracking-wide text-teal-900"
                      data-testid="rmc-claim-code"
                    >
                      {result.narrative.claimCode}
                    </span>
                    <button
                      type="button"
                      onClick={() => copyClaimCode(result.narrative!.claimCode!)}
                      className="inline-flex items-center gap-1.5 rounded-lg border border-teal-300 px-3 py-1.5 text-xs font-medium text-teal-800 hover:bg-teal-50"
                    >
                      <Copy className="h-3.5 w-3.5" /> {copied ? "Copied" : "Copy"}
                    </button>
                  </div>
                  {result.narrative.caseReference ? (
                    <p className="mt-3 text-xs text-teal-700">
                      Case reference:{" "}
                      <span className="font-mono font-semibold">{result.narrative.caseReference}</span>
                    </p>
                  ) : null}
                </div>
              ) : result.narrative && !result.narrative.filed ? (
                <div className="rounded-2xl border border-red-300 bg-red-50 p-6" data-testid="rmc-narrative-failed">
                  <div className="mb-2 flex items-center gap-2 font-semibold text-red-900">
                    <AlertTriangle className="h-5 w-5" /> Your written report was not filed
                  </div>
                  <p className="text-sm text-red-800">
                    {result.narrative.message ??
                      "Please keep what you wrote and try submitting again — it has not been recorded."}
                  </p>
                </div>
              ) : null}

              {result.scores ? (
                result.scores.stored ? (
                  <div
                    className="rounded-2xl border border-teal-300 bg-teal-50 p-5"
                    data-testid="rmc-scores-stored"
                  >
                    <div className="flex items-center gap-2 font-semibold text-teal-900">
                      <CheckCircle2 className="h-5 w-5" /> Your ratings were recorded
                    </div>
                    <p className="mt-1 text-sm text-teal-800">
                      {result.scores.measuresStored ?? 0} answer
                      {result.scores.measuresStored === 1 ? "" : "s"} saved
                      {result.scores.verifiedInteraction ? " and linked to a confirmed visit" : ""}.
                    </p>
                  </div>
                ) : (
                  <div
                    className="rounded-2xl border border-amber-200 bg-amber-50 p-5"
                    data-testid="rmc-scores-not-stored"
                  >
                    <div className="flex items-center gap-2 font-semibold text-amber-900">
                      <AlertTriangle className="h-5 w-5" /> Your ratings were not saved
                    </div>
                    <p className="mt-1 text-sm text-amber-800">
                      {result.scores.message ??
                        "The ratings could not be stored this time."}
                      {result.narrative?.filed
                        ? " Your written report above was filed and is not affected by this."
                        : ""}
                    </p>
                  </div>
                )
              ) : null}

              <div className="flex items-start gap-2 rounded-lg border border-border bg-muted/40 p-3 text-xs text-muted-foreground">
                <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-teal-600" />
                <span>
                  {result.anonymous
                    ? "You were not identified. Your written report and your ratings are kept separate on purpose, so neither can be used to work out who the other belongs to."
                    : "You chose to be identified with this submission."}
                </span>
              </div>

              <Link
                href="/my-life"
                className="inline-flex rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700"
              >
                Done
              </Link>
            </div>
          ) : instrument ? (
            <form onSubmit={handleSubmit} className="max-w-2xl space-y-6">
              {submitError && (
                <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                  {submitError}
                </div>
              )}

              <GlassSurface className="p-4">
                <p className="text-sm text-muted-foreground">
                  {instrument.appliesTo ??
                    "For any woman who received intrapartum or immediate postnatal care at a facility."}
                  {" "}Answer only what applies to you — every question is optional.
                </p>
              </GlassSurface>

              <div className="space-y-3">
                {instrument.measures.map((m) => (
                  <LikertRow
                    key={m.measure}
                    measure={m}
                    scale={instrument.scale}
                    value={answers[m.measure]}
                    onChange={(n) => setAnswer(m.measure, n)}
                  />
                ))}
              </div>

              <div>
                <label className="mb-1 block text-sm font-medium text-foreground">
                  Facility (optional)
                </label>
                <input
                  value={facilityId}
                  onChange={(e) => setFacilityId(e.target.value)}
                  placeholder="Facility name or ID"
                  className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                  data-testid="rmc-facility-id"
                />
              </div>

              <div>
                <label className="mb-1 block text-sm font-medium text-foreground">
                  In your own words (optional)
                </label>
                <textarea
                  value={narrative}
                  onChange={(e) => setNarrative(e.target.value)}
                  rows={5}
                  placeholder="Tell us what happened, in as much or little detail as you want"
                  className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                  data-testid="rmc-narrative"
                />
              </div>

              <label className="flex items-center gap-2 text-sm text-foreground">
                <input
                  type="checkbox"
                  checked={identifyMe}
                  onChange={(e) => setIdentifyMe(e.target.checked)}
                  data-testid="rmc-identify-me"
                />
                Identify me with this report (unchecked stays anonymous)
              </label>

              <div className="flex items-start gap-2 rounded-lg border border-border bg-muted/40 p-3 text-xs text-muted-foreground">
                <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-teal-600" />
                <span>
                  Anonymous by default — nobody caring for you will see who left this report unless
                  you choose to be identified. This never changes any provider&apos;s licence, scope
                  or access on its own.
                </span>
              </div>

              <button
                type="submit"
                disabled={!canSubmit}
                className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50"
                data-testid="rmc-submit"
              >
                {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
                {submitting ? "Submitting…" : "Submit"}
              </button>
            </form>
          ) : null}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
