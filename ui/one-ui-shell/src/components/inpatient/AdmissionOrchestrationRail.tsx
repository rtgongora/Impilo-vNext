"use client";

import Link from "next/link";
import { Loader2, Route, AlertCircle } from "lucide-react";
import { useCoreTransactionDetail } from "@/hooks/queries/useCoreTransactionExperience";
import { TransactionStateBadge, TransactionTypeBadge } from "@/features/core-transaction/components";

interface AdmissionOrchestrationRailProps {
  admissionRef: string;
  transactionId?: string;
}

export function AdmissionOrchestrationRail({ admissionRef, transactionId }: AdmissionOrchestrationRailProps) {
  const resolvedId = transactionId ?? `admission-${admissionRef}`;
  const { data: transaction, isLoading, isError } = useCoreTransactionDetail(resolvedId);

  if (isLoading) {
    return (
      <section className="rounded-xl border border-border bg-background px-4 py-3" data-testid="admission-orchestration-rail">
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading inpatient admission transaction…
        </div>
      </section>
    );
  }

  if (isError || !transaction) {
    return (
      <section className="rounded-xl border border-warning/35 bg-warning-soft px-4 py-3" data-testid="admission-orchestration-rail">
        <div className="flex items-start gap-2 text-sm text-warning-foreground">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
          <p>
            Admission transaction context unavailable for <span className="font-mono">{admissionRef}</span>.
            Clinical episode data may still load from inpatient-service.
          </p>
        </div>
      </section>
    );
  }

  return (
    <section
      className="rounded-xl border border-warning/35 bg-[linear-gradient(135deg,#faf5ff_0%,#ffffff_100%)] px-4 py-4 shadow-sm"
      data-testid="admission-orchestration-rail"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Route className="h-4 w-4 text-purple-600" />
          <h3 className="text-sm font-semibold text-foreground">Inpatient admission transaction</h3>
        </div>
        <div className="flex flex-wrap gap-2">
          <TransactionTypeBadge type={transaction.transaction.type} />
          <TransactionStateBadge state={transaction.transaction.currentState} />
        </div>
      </div>
      <p className="mt-2 text-xs text-muted-foreground">
        Admission: <span className="font-mono">{admissionRef}</span>
        {" · "}
        Correlation: <span className="font-mono">{transaction.auditSummary.correlationId}</span>
      </p>
      <p className="mt-3 text-xs text-muted-foreground">
        <Link href="/core-transaction" className="font-medium text-primary hover:underline">
          Open core transaction shell
        </Link>
        {" · "}
        <Link href={`/clinical/inpatient/discharge/${admissionRef}`} className="font-medium text-primary hover:underline">
          Discharge workflow
        </Link>
      </p>
    </section>
  );
}
