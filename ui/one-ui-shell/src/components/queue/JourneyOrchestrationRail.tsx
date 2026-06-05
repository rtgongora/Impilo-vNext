"use client";

import Link from "next/link";
import { Loader2, Route, AlertCircle } from "lucide-react";
import {
  useApplyCoreTransactionAction,
  useCoreTransactionDetail,
} from "@/hooks/queries/useCoreTransactionExperience";
import {
  JourneyNextActionPanel,
  TransactionStateBadge,
  TransactionTypeBadge,
} from "@/features/core-transaction/components";

interface JourneyOrchestrationRailProps {
  transactionId: string;
  patientId: string;
  journeyId?: string;
}

export function JourneyOrchestrationRail({
  transactionId,
  patientId,
  journeyId,
}: JourneyOrchestrationRailProps) {
  const { data: transaction, isLoading, isError } = useCoreTransactionDetail(transactionId);
  const applyAction = useApplyCoreTransactionAction();

  if (isLoading) {
    return (
      <section
        className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3"
        data-testid="journey-orchestration-rail"
        aria-busy="true"
      >
        <div className="flex items-center gap-2 text-sm text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading walk-in journey transaction…
        </div>
      </section>
    );
  }

  if (isError) {
    return (
      <section
        className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3"
        data-testid="journey-orchestration-rail"
      >
        <div className="flex items-start gap-2 text-sm text-amber-800">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
          <p>Walk-in journey context unavailable. Queue entry was created; retry when Experience BFF is reachable.</p>
        </div>
      </section>
    );
  }

  if (!transaction) {
    return (
      <section
        className="rounded-xl border border-slate-200 bg-white px-4 py-3"
        data-testid="journey-orchestration-rail"
      >
        <div className="flex items-start gap-2">
          <Route className="mt-0.5 h-4 w-4 text-impilo-600" />
          <div className="space-y-1 text-sm">
            <p className="font-medium text-slate-900">Walk-in journey not linked yet</p>
            <p className="text-slate-600">
              {journeyId
                ? `Journey ${journeyId} was queued. Start an encounter below to continue the outpatient spine.`
                : "Complete walk-in registration to attach a PCT journey transaction."}
            </p>
            <Link href={`/queue/walk-in?patientId=${patientId}`} className="text-xs font-medium text-impilo-600 hover:underline">
              Return to walk-in registration
            </Link>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section
      className="rounded-xl border border-emerald-200 bg-[linear-gradient(135deg,#f6fffb_0%,#ffffff_100%)] px-4 py-4 shadow-sm"
      data-testid="journey-orchestration-rail"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Route className="h-4 w-4 text-emerald-600" />
          <h3 className="text-sm font-semibold text-slate-900">Walk-in queue journey</h3>
        </div>
        <div className="flex flex-wrap gap-2">
          <TransactionTypeBadge type={transaction.transaction.type} />
          <TransactionStateBadge state={transaction.transaction.currentState} />
        </div>
      </div>

      <p className="mt-2 text-xs text-slate-600">
        Person stage: <span className="font-medium">{transaction.journeys.person.currentStage}</span>
        {" · "}
        Provider stage: <span className="font-medium">{transaction.journeys.provider.currentStage}</span>
        {journeyId ? (
          <>
            {" · "}
            Journey: <span className="font-mono">{journeyId}</span>
          </>
        ) : null}
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
                      payload: { patientId, journeyId, source: "journey-orchestration-rail" },
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
        <Link href="/core-transaction" className="font-medium text-impilo-600 hover:underline">
          Open core transaction shell
        </Link>
        {" · "}
        Next: start encounter to link clinical delivery
      </p>
    </section>
  );
}
