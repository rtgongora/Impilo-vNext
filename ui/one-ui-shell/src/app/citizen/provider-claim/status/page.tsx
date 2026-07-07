"use client";

/**
 * Provider access request status (IATG Journey D, choice g — "Check status of my request").
 * Route: /citizen/provider-claim/status — the applicant's durable requests with their
 * current status, the next responsible actor, and an honest reason. No data goes nowhere.
 */

import Link from "next/link";
import { ArrowLeft, Clock, ListChecks, Loader2 } from "lucide-react";
import { useProviderAccessRequests } from "@/hooks/queries/useProviderAccessRequest";

function StatusBadge({ status }: { status: string }) {
  const terminal = status === "APPROVED" || status === "REJECTED";
  const tone = status === "APPROVED"
    ? "border-emerald-300 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-400"
    : status === "REJECTED" || status === "DUPLICATE_SUSPECTED"
      ? "border-destructive/40 bg-destructive/5 text-foreground"
      : "border-amber-300 bg-amber-50 text-amber-700 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-400";
  return (
    <span className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium ${tone}`}>
      {!terminal ? <Clock className="h-3 w-3" /> : null}
      {status}
    </span>
  );
}

export default function ProviderAccessStatusPage() {
  const { data, isLoading, isError } = useProviderAccessRequests();

  return (
    <div className="mx-auto max-w-2xl space-y-4 px-4 py-6">
      <div>
        <Link
          href="/citizen/provider-claim"
          className="inline-flex items-center gap-1 text-xs font-medium text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-3.5 w-3.5" /> Back to Request Provider Access
        </Link>
        <h1 className="mt-2 flex items-center gap-2 text-lg font-semibold text-foreground">
          <ListChecks className="h-5 w-5 text-primary" /> Your provider-access requests
        </h1>
        <p className="text-sm text-muted-foreground">
          Every request you submit is recorded here with its status and the next responsible actor.
        </p>
      </div>

      {isLoading ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground" aria-busy>
          <Loader2 className="h-4 w-4 animate-spin" /> Loading your requests…
        </div>
      ) : isError ? (
        <div
          role="alert"
          className="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-700 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-400"
        >
          Your requests could not be loaded right now. Nothing was changed — retry once the service is reachable.
        </div>
      ) : !data || data.length === 0 ? (
        <div
          className="rounded-lg border border-border bg-background px-3 py-4 text-sm text-muted-foreground"
          data-testid="provider-access-status-empty"
        >
          You have no provider-access requests yet.{" "}
          <Link href="/citizen/provider-claim" className="font-medium text-primary hover:text-primary-hover">
            Start one
          </Link>
          .
        </div>
      ) : (
        <ul className="space-y-3" data-testid="provider-access-status-list">
          {data.map((r) => (
            <li key={r.publicId} className="rounded-lg border border-border bg-card px-4 py-3">
              <div className="flex items-center justify-between gap-2">
                <span className="font-mono text-sm text-foreground">{r.publicId}</span>
                <StatusBadge status={r.status} />
              </div>
              <dl className="mt-2 grid grid-cols-[auto,1fr] gap-x-3 gap-y-1 text-xs text-muted-foreground">
                <dt>Type</dt>
                <dd className="text-foreground">{r.requestType}</dd>
                {r.nextActor ? (
                  <>
                    <dt>Next actor</dt>
                    <dd className="text-foreground">{r.nextActor}</dd>
                  </>
                ) : null}
                {r.councilNumberMasked ? (
                  <>
                    <dt>Council no.</dt>
                    <dd className="font-mono text-foreground">{r.councilNumberMasked}</dd>
                  </>
                ) : null}
                {r.ecNumberMasked ? (
                  <>
                    <dt>EC no.</dt>
                    <dd className="font-mono text-foreground">{r.ecNumberMasked}</dd>
                  </>
                ) : null}
              </dl>
              {r.reason ? <p className="mt-2 text-xs text-muted-foreground">{r.reason}</p> : null}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
