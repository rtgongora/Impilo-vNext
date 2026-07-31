"use client";

/**
 * W12 — /my/pregnancy — the citizen's own pregnancy: current status, and booking one.
 *
 * BFF: /internal/v1/confidential/maternity/pregnancy-episodes[/{cpid}[/current]].
 * The browser never holds a CPID (Identity Contract §12) — every call goes through the BFF's
 * "me" sentinel so the server resolves her subject from her actor identity.
 *
 * Status semantics this page must get right (see useConfidentialMaternity.ts and
 * docs/clinical/rmnp/citizen-pregnancy-smbp-mobile-contract.md):
 *   - 201 created and 200 replayed-offline-packet are both success — never a duplicate warning;
 *   - 409 (she already has an open pregnancy) is a reconciliation outcome, not a failure banner;
 *   - 422 (undatable — no LMP, no scan) asks her for one more fact, not a generic error;
 *   - risk_status/NOT_ASSESSED is rendered exactly as recorded, never as "low risk".
 */

import { useState } from "react";
import Link from "next/link";
import { AlertTriangle, Baby, CalendarHeart, CheckCircle2, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  isPregnancyConflict,
  isPregnancyUndatable,
  useCurrentPregnancyEpisode,
  useOpenPregnancyEpisode,
  usePregnancyEpisodes,
  type DatingMethod,
  type PregnancyEpisodeView,
} from "@/hooks/queries/useConfidentialMaternity";

function statusLabel(status: string): string {
  switch (status) {
    case "ONGOING":
      return "Ongoing";
    case "DELIVERED":
      return "Delivered";
    case "LOST":
      return "Ended in loss";
    case "TERMINATED":
      return "Ended";
    default:
      return status.replace(/_/g, " ").toLowerCase();
  }
}

/** risk_status is never rendered as reassurance when NOT_ASSESSED — §4 contract rule. */
function riskStatusNotice(riskStatus?: string | null): string | null {
  if (!riskStatus || riskStatus === "NOT_ASSESSED") {
    return "Your pregnancy risk has not been assessed yet — this is not the same as being told you are low-risk.";
  }
  return null;
}

function CurrentPregnancyCard({ episode }: { episode: PregnancyEpisodeView }) {
  const risk = riskStatusNotice(episode.risk_status);
  return (
    <section className="rounded-2xl border border-rose-200 bg-white p-6 shadow-sm">
      <div className="flex items-center gap-2">
        <Baby className="h-5 w-5 text-rose-600" />
        <h2 className="text-base font-semibold text-slate-900">
          Your current pregnancy — {statusLabel(episode.status)}
        </h2>
      </div>

      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-slate-500">
            Estimated delivery date
          </p>
          <p className="text-sm text-slate-800">
            {episode.estimated_delivery_date
              ? new Date(episode.estimated_delivery_date).toLocaleDateString()
              : "Not yet estimated"}
          </p>
        </div>
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-slate-500">
            Dated from
          </p>
          <p className="text-sm text-slate-800">
            {episode.dating_method
              ? `${episode.dating_method.replace(/_/g, " ").toLowerCase()}${
                  episode.dating_confidence ? ` (${episode.dating_confidence.toLowerCase()} confidence)` : ""
                }`
              : "Not recorded"}
          </p>
        </div>
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-slate-500">
            Pregnancy start
          </p>
          <p className="text-sm text-slate-800">
            {episode.pregnancy_start_date
              ? new Date(episode.pregnancy_start_date).toLocaleDateString()
              : "Not recorded"}
          </p>
        </div>
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-slate-500">
            Multiple pregnancy
          </p>
          <p className="text-sm text-slate-800">
            {episode.plurality && episode.plurality > 1
              ? `Yes — ${episode.plurality}`
              : episode.plurality === 1
                ? "No — single"
                : "Not recorded"}
          </p>
        </div>
      </div>

      {risk ? (
        <div className="mt-4 flex items-start gap-2 rounded-xl border border-slate-200 bg-slate-50 p-3 text-sm text-slate-600">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <span>{risk}</span>
        </div>
      ) : (
        <p className="mt-4 text-xs text-slate-500">
          Recorded risk status: {episode.risk_status?.replace(/_/g, " ").toLowerCase()}.
        </p>
      )}

      <div className="mt-4">
        <Link
          href="/my/monitoring"
          className="text-xs font-medium text-rose-700 hover:underline"
        >
          If your care team has started home blood-pressure monitoring for this pregnancy, view it
          on My Monitoring →
        </Link>
      </div>
    </section>
  );
}

type BookingOutcome =
  | { kind: "success"; episode: PregnancyEpisodeView }
  | { kind: "conflict"; existingId?: string; task?: string; message?: string }
  | { kind: "undatable"; message?: string }
  | { kind: "error"; message: string };

function BookPregnancyForm({ onBooked }: { onBooked: () => void }) {
  const openEpisode = useOpenPregnancyEpisode();
  const [method, setMethod] = useState<DatingMethod>("LMP");
  const [lmpDate, setLmpDate] = useState("");
  const [lmpCertain, setLmpCertain] = useState(true);
  const [scanDate, setScanDate] = useState("");
  const [scanGaDays, setScanGaDays] = useState("");
  const [outcome, setOutcome] = useState<BookingOutcome | null>(null);

  const canSubmit =
    method === "LMP" ? !!lmpDate : method === "ULTRASOUND" ? !!scanDate && !!scanGaDays : false;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setOutcome(null);

    const datingBases =
      method === "LMP"
        ? [{ method: "LMP" as const, lastMenstrualPeriod: lmpDate, lastMenstrualPeriodCertain: lmpCertain }]
        : [
            {
              method: "ULTRASOUND" as const,
              observedOn: scanDate,
              measuredGestationalAgeDays: Number(scanGaDays),
            },
          ];

    try {
      const episode = await openEpisode.mutateAsync({ datingBases });
      setOutcome({ kind: "success", episode });
      onBooked();
    } catch (err) {
      if (isPregnancyConflict(err)) {
        setOutcome({
          kind: "conflict",
          existingId: err.data?.existing_pregnancy_episode_id,
          task: err.data?.reconciliation_task,
          message: err.data?.message,
        });
      } else if (isPregnancyUndatable(err)) {
        setOutcome({ kind: "undatable", message: err.data?.message });
      } else {
        setOutcome({
          kind: "error",
          message: "We couldn't book this right now. Please try again in a moment.",
        });
      }
    }
  };

  if (outcome?.kind === "success") {
    return (
      <div className="rounded-2xl border border-emerald-300 bg-emerald-50 p-6" data-testid="booking-success">
        <div className="mb-2 flex items-center gap-2 font-semibold text-emerald-900">
          <CheckCircle2 className="h-5 w-5" />
          {outcome.episode.replayed ? "Already recorded" : "Pregnancy recorded"}
        </div>
        <p className="text-sm text-emerald-800">
          {outcome.episode.replayed
            ? "This booking had already been saved — nothing new was created, and no information was lost."
            : "Your pregnancy has been recorded. Your care team will use this to plan your antenatal visits."}
        </p>
      </div>
    );
  }

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="flex items-center gap-2 text-base font-semibold text-slate-900">
        <CalendarHeart className="h-5 w-5 text-rose-600" /> Record a pregnancy
      </h2>
      <p className="mt-1 text-sm text-slate-600">
        Tell us either your last menstrual period, or the date and result of a dating scan — we
        need at least one to estimate your delivery date.
      </p>

      {outcome?.kind === "conflict" ? (
        <div
          className="mt-4 rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900"
          data-testid="booking-conflict"
        >
          <p className="font-semibold">You already have a pregnancy on record</p>
          <p className="mt-1">
            {outcome.message ??
              "It looks like you already have an open pregnancy recorded. We won't create a second one."}
          </p>
          {outcome.task ? <p className="mt-1 text-xs text-amber-800">{outcome.task}</p> : null}
          {outcome.existingId ? (
            <p className="mt-1 text-xs text-amber-700">
              Reference: <span className="font-mono">{outcome.existingId}</span>
            </p>
          ) : null}
        </div>
      ) : null}

      {outcome?.kind === "undatable" ? (
        <div
          className="mt-4 rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900"
          data-testid="booking-undatable"
        >
          <p className="font-semibold">We need one more detail</p>
          <p className="mt-1">
            {outcome.message ??
              "We couldn't estimate a delivery date from what was entered — please provide a last menstrual period date or dating scan."}
          </p>
        </div>
      ) : null}

      {outcome?.kind === "error" ? (
        <div className="mt-4 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-800">
          {outcome.message}
        </div>
      ) : null}

      <form onSubmit={submit} className="mt-4 space-y-4" data-testid="pregnancy-booking-form">
        <div className="flex gap-4">
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input
              type="radio"
              name="dating-method"
              checked={method === "LMP"}
              onChange={() => setMethod("LMP")}
            />
            Last menstrual period
          </label>
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input
              type="radio"
              name="dating-method"
              checked={method === "ULTRASOUND"}
              onChange={() => setMethod("ULTRASOUND")}
            />
            Dating scan
          </label>
        </div>

        {method === "LMP" ? (
          <div className="space-y-2">
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">
                First day of your last period
              </label>
              <input
                type="date"
                value={lmpDate}
                onChange={(e) => setLmpDate(e.target.value)}
                required
                data-testid="lmp-date-input"
                className="w-full max-w-xs rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <label className="flex items-start gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={lmpCertain}
                onChange={(e) => setLmpCertain(e.target.checked)}
                className="mt-0.5"
                data-testid="lmp-certain-checkbox"
              />
              <span>I&apos;m fairly certain about this date</span>
            </label>
          </div>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Scan date</label>
              <input
                type="date"
                value={scanDate}
                onChange={(e) => setScanDate(e.target.value)}
                required
                data-testid="scan-date-input"
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">
                Gestational age on scan (days)
              </label>
              <input
                type="number"
                min="1"
                value={scanGaDays}
                onChange={(e) => setScanGaDays(e.target.value)}
                required
                data-testid="scan-ga-days-input"
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
              <p className="mt-1 text-xs text-slate-500">
                Ask your clinician or check your scan report for this number.
              </p>
            </div>
          </div>
        )}

        <button
          type="submit"
          disabled={!canSubmit || openEpisode.isPending}
          data-testid="pregnancy-booking-submit"
          className="inline-flex items-center gap-2 rounded-lg bg-rose-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-rose-700 disabled:opacity-50"
        >
          {openEpisode.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
          {openEpisode.isPending ? "Recording…" : "Record this pregnancy"}
        </button>
      </form>
    </section>
  );
}

function PregnancyHistory() {
  const history = usePregnancyEpisodes();
  const past = (history.data ?? []).filter((e) => e.status !== "ONGOING");
  if (history.isLoading || past.length === 0) return null;

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="text-sm font-medium text-slate-700">Previous pregnancies</h2>
      <ul className="mt-2 divide-y divide-slate-100">
        {past.map((e) => (
          <li key={e.pregnancy_episode_id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
            <span className="text-slate-800">{statusLabel(e.status)}</span>
            <span className="text-xs text-slate-500">
              {e.ended_on
                ? new Date(e.ended_on).toLocaleDateString()
                : e.pregnancy_start_date
                  ? `Started ${new Date(e.pregnancy_start_date).toLocaleDateString()}`
                  : ""}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}

export default function MyPregnancyPage() {
  const current = useCurrentPregnancyEpisode();

  return (
    <AppLayout>
      <PageShell
        title="My pregnancy"
        subtitle="Your current pregnancy, and recording a new one — explained in plain language."
        icon={<Baby className="h-5 w-5" />}
        density="hero"
      >
        <div className="space-y-4">
          {current.isLoading ? (
            <p className="flex items-center gap-2 py-8 text-sm text-slate-500">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading your pregnancy record…
            </p>
          ) : current.error ? (
            <div className="rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-600">
              We couldn&apos;t load your pregnancy record right now. Please try again in a moment.
            </div>
          ) : current.data ? (
            <CurrentPregnancyCard episode={current.data} />
          ) : (
            <BookPregnancyForm onBooked={() => void current.refetch()} />
          )}

          <PregnancyHistory />
        </div>
      </PageShell>
    </AppLayout>
  );
}
