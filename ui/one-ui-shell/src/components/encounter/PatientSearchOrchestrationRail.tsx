"use client";

import Link from "next/link";
import { AlertCircle, Loader2, Route, UserCheck } from "lucide-react";
import {
  useApplyCoreTransactionAction,
  useCoreTransactionDetail,
} from "@/hooks/queries/useCoreTransactionExperience";
import {
  JourneyNextActionPanel,
  TransactionStateBadge,
  TransactionTypeBadge,
} from "@/features/core-transaction/components";

interface PatientSearchOrchestrationRailProps {
  transactionId?: string | null;
  journeyId?: string | null;
  selectedPatientId?: string | null;
  selectedPatientLabel?: string | null;
  compact?: boolean;
}

export function PatientSearchOrchestrationRail({
  transactionId,
  journeyId,
  selectedPatientId,
  selectedPatientLabel,
  compact = false,
}: PatientSearchOrchestrationRailProps) {
  const activeTransactionId = transactionId?.trim() ?? "";
  const { data: transaction, isLoading, isError } = useCoreTransactionDetail(activeTransactionId);
  const applyAction = useApplyCoreTransactionAction();

  if (!activeTransactionId) {
    return (
      <section
        className="rounded-xl border border-border bg-card px-4 py-3"
        data-testid="patient-search-orchestration-rail"
      >
        <div className="flex items-start gap-2 text-sm">
          <Route className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
          <div className="space-y-1">
            <p className="font-medium text-foreground">Patient search orchestration</p>
            <p className="text-muted-foreground">
              Search matches route into chart entry, walk-in registration, or specialty entry (LIMS/PACS)
              while preserving facility context.
            </p>
          </div>
        </div>
      </section>
    );
  }

  if (isLoading) {
    return (
      <section
        className="rounded-xl border border-border bg-background px-4 py-3"
        data-testid="patient-search-orchestration-rail"
        aria-busy="true"
      >
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading patient-selection transaction context…
        </div>
      </section>
    );
  }

  if (isError) {
    return (
      <section
        className="rounded-xl border border-warning/35 bg-warning-soft px-4 py-3"
        data-testid="patient-search-orchestration-rail"
      >
        <div className="flex items-start gap-2 text-sm text-warning-foreground">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
          <p>
            Core transaction context unavailable for this search session. Continue patient lookup; retry when
            Experience BFF is reachable.
          </p>
        </div>
      </section>
    );
  }

  if (!transaction) {
    return (
      <section
        className="rounded-xl border border-border bg-card px-4 py-3"
        data-testid="patient-search-orchestration-rail"
      >
        <p className="text-sm text-muted-foreground">
          Transaction <span className="font-mono">{activeTransactionId}</span> was not found. Search can continue
          without journey correlation.
        </p>
      </section>
    );
  }

  const denied = transaction.permissions.some(
    (permission) => permission.code === "VIEW_TRANSACTION" && permission.allowed === false,
  );

  if (denied) {
    return (
      <section
        className="rounded-xl border border-warning/35 bg-warning-soft px-4 py-3"
        data-testid="patient-search-orchestration-rail"
      >
        <p className="text-sm text-warning-foreground">You do not have permission to view this search transaction context.</p>
      </section>
    );
  }

  return (
    <section
      className="rounded-xl border border-primary/25 bg-[linear-gradient(135deg,#f8fbff_0%,#ffffff_100%)] px-4 py-4 shadow-sm"
      data-testid="patient-search-orchestration-rail"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Route className="h-4 w-4 text-primary" />
          <h3 className="text-sm font-semibold text-foreground">
            {compact ? "Search-linked transaction" : "Patient search transaction journey"}
          </h3>
        </div>
        <div className="flex flex-wrap gap-2">
          <TransactionTypeBadge type={transaction.transaction.type} />
          <TransactionStateBadge state={transaction.transaction.currentState} />
        </div>
      </div>

      <p className="mt-2 text-xs text-muted-foreground">
        Provider stage: <span className="font-medium">{transaction.journeys.provider.currentStage}</span>
        {" · "}
        Person stage: <span className="font-medium">{transaction.journeys.person.currentStage}</span>
        {journeyId ? (
          <>
            {" · "}
            Journey: <span className="font-mono">{journeyId}</span>
          </>
        ) : null}
        {" · "}
        Correlation: <span className="font-mono">{transaction.auditSummary.correlationId}</span>
      </p>

      {selectedPatientId ? (
        <div className="mt-3 rounded-lg border border-success/25 bg-success-soft px-3 py-2 text-sm text-primary-hover">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p>
              <UserCheck className="mr-1 inline h-4 w-4" />
              Selected patient:{" "}
              <span className="font-medium">{selectedPatientLabel ?? selectedPatientId}</span>
            </p>
            <button
              type="button"
              className="rounded-lg border border-emerald-300 bg-card px-3 py-1.5 text-xs font-medium text-primary-hover hover:bg-emerald-100 disabled:opacity-50"
              disabled={applyAction.isPending}
              onClick={() => {
                applyAction.mutate({
                  transactionId: transaction.transaction.id,
                  actionCode: "RESOLVE_IDENTITY",
                  payload: {
                    patientId: selectedPatientId,
                    source: "patient-search-orchestration-rail",
                  },
                });
              }}
            >
              {applyAction.isPending ? "Resolving…" : "Resolve identity on transaction"}
            </button>
          </div>
        </div>
      ) : (
        <p className="mt-3 text-xs text-muted-foreground">
          Select a patient from search results to bind identity before opening the chart or queue handoff.
        </p>
      )}

      {!compact && transaction.nextActions.length > 0 ? (
        <div className="mt-3 space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Next trusted actions</p>
          <ul className="flex flex-wrap gap-2">
            {transaction.nextActions.map((action) => (
              <li key={action.code}>
                <button
                  type="button"
                  className="rounded-lg border border-border bg-card px-3 py-1.5 text-xs font-medium text-foreground hover:bg-background disabled:opacity-50"
                  disabled={applyAction.isPending}
                  onClick={() => {
                    applyAction.mutate({
                      transactionId: transaction.transaction.id,
                      actionCode: action.code,
                      payload: {
                        patientId: selectedPatientId ?? undefined,
                        source: "patient-search-orchestration-rail",
                      },
                    });
                  }}
                >
                  {applyAction.isPending ? "Applying…" : action.label}
                </button>
              </li>
            ))}
          </ul>
        </div>
      ) : !compact ? (
        <div className="mt-3">
          <JourneyNextActionPanel transaction={transaction} />
        </div>
      ) : null}

      {!compact ? (
        <p className="mt-3 text-xs text-muted-foreground">
          <Link href="/core-transaction" className="font-medium text-primary hover:underline">
            Open core transaction shell
          </Link>
        </p>
      ) : null}
    </section>
  );
}
