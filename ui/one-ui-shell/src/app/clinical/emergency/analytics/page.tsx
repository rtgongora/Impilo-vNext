"use client";

/**
 * Emergency analytics — W17 Theatre-pattern indicators.
 *
 * Governed report keys run against rpt_emergency_episode_metric (never pct.*).
 * Measures that are still NOT_COMPUTABLE are named without zeros — a zero on an
 * emergency dashboard is read as "no deaths", not as "no pipeline".
 */

import Link from "next/link";
import { useState } from "react";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useGenerateReport } from "@/hooks/queries/useReports";

type LiveMeasure = {
  name: string;
  reportKey: string;
  dsecElement: string;
  status: "IMPLEMENTED" | "PARTIAL";
  limit?: string;
};

type AbsentMeasure = {
  name: string;
  dsecElement: string;
  blockedBy: string;
};

const LIVE_MEASURES: LiveMeasure[] = [
  {
    name: "Emergency episode summary",
    reportKey: "emergency-episode-summary",
    dsecElement: "Disposition; outcome; alerts; RITO linkage",
    status: "IMPLEMENTED",
  },
  {
    name: "Disposition mix",
    reportKey: "emergency-disposition-mix",
    dsecElement: "Disposition",
    status: "IMPLEMENTED",
  },
  {
    name: "Triage acuity distribution",
    reportKey: "emergency-acuity-distribution",
    dsecElement: "Triage category",
    status: "PARTIAL",
    limit: "Episodes without a projected acuity bucket as NOT_YET_TRIAGED — never a fabricated colour.",
  },
  {
    name: "Emergency episode register",
    reportKey: "emergency-episode-register",
    dsecElement: "Episode identity; arrival; state",
    status: "IMPLEMENTED",
  },
];

const ABSENT_MEASURES: AbsentMeasure[] = [
  {
    name: "Resuscitation interval adherence",
    dsecElement: "Interventions performed",
    blockedBy: "Resuscitation timings live on inpatient records and are not projected yet",
  },
  {
    name: "Observation and short-stay conversion",
    dsecElement: "Disposition",
    blockedBy: "Observation stay rows have no projection into rpt_emergency_episode_metric",
  },
  {
    name: "Critical result acknowledgement time",
    dsecElement: "Diagnostics ordered and resulted",
    blockedBy: "pct.emergency.critical_result is routed but not yet projected into the metric table",
  },
];

export default function EmergencyAnalyticsPage() {
  const generate = useGenerateReport();
  const [lastKey, setLastKey] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  return (
    <AppLayout>
      <PageShell
        title="Emergency analytics"
        subtitle="W17 — Theatre-pattern indicators from rpt_emergency_episode_metric (EMS.DSEC.MINIMUM_DATASET)"
      >
        <div className="mb-4 flex gap-4 text-sm">
          <Link href="/clinical/emergency/command" className="text-primary underline">
            Emergency command
          </Link>
          <Link href="/clinical/emergency/board" className="text-primary underline">
            Episode board
          </Link>
        </div>

        <div
          className="mb-6 rounded-lg border border-border bg-card p-4 text-sm"
          data-testid="analytics-w17-live"
        >
          <p className="font-medium text-foreground">
            Live report keys (query the Kafka projection — never pct.* directly)
          </p>
          <p className="mt-1 text-muted-foreground">
            Mapping:{" "}
            <code className="text-xs">docs/clinical/emergency-domain-pack/dsec-element-mapping.json</code>
          </p>
        </div>

        <h2 className="mb-2 text-sm font-semibold text-foreground">
          Computable measures ({LIVE_MEASURES.length})
        </h2>
        <ul className="mb-8 space-y-2" data-testid="analytics-live-measures">
          {LIVE_MEASURES.map((measure) => (
            <li
              key={measure.reportKey}
              className="rounded border border-border bg-card p-3 text-sm"
              data-testid="analytics-live-measure"
            >
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-medium text-foreground">{measure.name}</p>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    Key: <code>{measure.reportKey}</code> · {measure.status}
                  </p>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    Derived from: {measure.dsecElement}
                  </p>
                  {measure.limit ? (
                    <p className="mt-0.5 text-xs text-amber-800">Limit: {measure.limit}</p>
                  ) : null}
                </div>
                <button
                  type="button"
                  className="rounded bg-primary px-3 py-1.5 text-xs text-white disabled:opacity-50"
                  disabled={generate.isPending}
                  data-testid={`run-report-${measure.reportKey}`}
                  onClick={() => {
                    setError(null);
                    setLastKey(measure.reportKey);
                    generate.mutate(
                      { report_type: measure.reportKey },
                      {
                        onError: (err) => {
                          const message =
                            err && typeof err === "object" && "error" in err
                              ? String((err as { error?: { message?: string } }).error?.message ?? err)
                              : String(err);
                          setError(message);
                        },
                      },
                    );
                  }}
                >
                  Queue report
                </button>
              </div>
            </li>
          ))}
        </ul>

        {lastKey && generate.isSuccess ? (
          <p className="mb-4 text-sm text-foreground" data-testid="analytics-report-queued">
            Queued <code>{lastKey}</code>
            {generate.data?.data?.id ? ` — job ${generate.data.data.id}` : ""}.
          </p>
        ) : null}
        {error ? (
          <p className="mb-4 text-sm text-danger" role="alert" data-testid="analytics-report-error">
            {error}
          </p>
        ) : null}

        <h2 className="mb-2 text-sm font-semibold text-foreground">
          Still not computable ({ABSENT_MEASURES.length})
        </h2>
        <ul className="space-y-2" data-testid="analytics-pending-measures">
          {ABSENT_MEASURES.map((measure) => (
            <li
              key={measure.name}
              className="rounded border border-border bg-card p-3 text-sm"
              data-testid="analytics-pending-measure"
            >
              <p className="font-medium text-foreground">{measure.name}</p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                Derived from: {measure.dsecElement}
              </p>
              <p className="mt-0.5 text-xs text-muted-foreground">Blocked by: {measure.blockedBy}</p>
            </li>
          ))}
        </ul>
      </PageShell>
    </AppLayout>
  );
}
