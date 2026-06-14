"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { CoreTransactionShell } from "@/features/core-transaction/components";
import { useCoreTransactionDetail } from "@/hooks/queries/useCoreTransactionExperience";

export default function CoreTransactionDetailPage() {
  const params = useParams();
  const transactionId = String(params.transactionId ?? "");
  const { data: transaction, isLoading, isError } = useCoreTransactionDetail(transactionId);

  return (
    <AppLayout>
      <PageShell title="Core transaction detail" subtitle={transactionId}>
        <Link
          href="/core-transaction"
          className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to core transactions
        </Link>
        <CoreTransactionShell
          transaction={transaction ?? undefined}
          status={
            isLoading ? "loading" : isError ? "error" : transaction ? "ready" : "empty"
          }
        />
      </PageShell>
    </AppLayout>
  );
}
