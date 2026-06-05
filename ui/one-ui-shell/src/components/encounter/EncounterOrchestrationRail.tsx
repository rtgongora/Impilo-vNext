"use client";

import Link from "next/link";
import { Loader2, Route, AlertCircle } from "lucide-react";
import {
  useApplyCoreTransactionAction,
  useEncounterCoreTransaction,
} from "@/hooks/queries/useCoreTransactionExperience";
import {
  JourneyNextActionPanel,
  TransactionStateBadge,
  TransactionTypeBadge,
} from "@/features/core-transaction/components";

interface EncounterOrchestrationRailProps {
  encounterId: string;
  patientId: string;
}

export function EncounterOrchestrationRail({ encounterId, patientId }: EncounterOrchestrationRailProps) {
  const { transaction, isLoading, isError } = useEncounterCoreTransaction(encounterId);
  const applyAction = useApplyCoreTransactionAction();

  if (isLoading) {
    return (
      <section
        className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3"
        data-testid="encounter-orchestration-rail"
        aria-busy="true"
      >
        <div className="flex items-center gap-2 text-sm text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading encounter transaction context…
        </div>
      </section>
    );
  }

  if (isError) {
    return (
      <section
        className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3"
        data-testid="encounter-orchestration-rail"
      >
        <div className="flex items-start gap-2 text-sm text-amber-800">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
          <p>
            Core transaction context unavailable. Clinical capture continues; retry when Experience BFF is reachable.
          </p>
        </div>
      </section>
    );
  }

  if (!transaction) {
    return (
      <section
        className="rounded-xl border border-slate-200 bg-white px-4 py-3"
        data-testid="encounter-orchestration-rail"
      >
        <div className="flex items-start gap-2">
          <Route className="mt-0.5 h-4 w-4 text-impilo-600" />
          <div className="space-y-1 text-sm">
            <p className="font-medium text-slate-900">Encounter transaction not linked yet</p>
            <p className="text-slate-600">
              Start the encounter from the encounters list after walk-in registration, or confirm the encounter exists
              in PCT for this facility.
            </p>
            <Link href={`/ehr/${patientId}/encounters`} className="text-xs font-medium text-impilo-600 hover:underline">
              Review encounter history
            </Link>
          </div>
        </div>
      </section>
    );
  }

  const denied = transaction.permissions.some(
    (permission) => permission.code === "VIEW_TRANSACTION" && permission.allowed === false,
  );

  if (denied) {
    return (
      <section
        className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3"
        data-testid="encounter-orchestration-rail"
      >
        <p className="text-sm text-amber-800">You do not have permission to view this encounter transaction context.</p>
      </section>
    );
  }

  return (
    <section
      className="rounded-xl border border-impilo-200 bg-[linear-gradient(135deg,#f8fbff_0%,#ffffff_100%)] px-4 py-4 shadow-sm"
      data-testid="encounter-orchestration-rail"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Route className="h-4 w-4 text-impilo-600" />
          <h3 className="text-sm font-semibold text-slate-900">Encounter transaction journey</h3>
        </div>
        <div className="flex flex-wrap gap-2">
          <TransactionTypeBadge type={transaction.transaction.type} />
          <TransactionStateBadge state={transaction.transaction.currentState} />
        </div>
      </div>

      <p className="mt-2 text-xs text-slate-600">
        Provider stage: <span className="font-medium">{transaction.journeys.provider.currentStage}</span>
        {" · "}
        Person stage: <span className="font-medium">{transaction.journeys.person.currentStage}</span>
        {" · "}
        Correlation: <span className="font-mono">{transaction.auditSummary.correlationId}</span>
      </p>

      {transaction.nextActions.length > 0 ? (
        <div className="mt-3 space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Next trusted actions</p>
          <ul className="flex flex-wrap gap-2">
            {transaction.nextActions.map((action) => (
              <li key={action.code}>
                <button
                  type="button"
                  className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                  disabled={applyAction.isPending}
                  onClick={() => {
                    applyAction.mutate({
                      transactionId: transaction.transaction.id,
                      actionCode: action.code,
                      payload: { encounterId, patientId, source: "encounter-orchestration-rail" },
                    });
                  }}
                >
                  {applyAction.isPending ? "Applying…" : action.label}
                </button>
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <div className="mt-3">
          <JourneyNextActionPanel transaction={transaction} />
        </div>
      )}

      <p className="mt-3 text-xs text-slate-500">
        <Link
          href={`/core-transaction`}
          className="font-medium text-impilo-600 hover:underline"
        >
          Open core transaction shell
        </Link>
        {" · "}
        Orders pending: {transaction.clinicalContext.ordersPending ?? 0}
      </p>
    </section>
  );
}
