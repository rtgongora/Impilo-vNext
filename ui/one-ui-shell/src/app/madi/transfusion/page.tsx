"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Activity, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useStartTransfusion } from "@/hooks/queries/useMadi";

export default function TransfusionPage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const start = useStartTransfusion();
  const [patientCpid, setPatientCpid] = useState("");
  const [orderId, setOrderId] = useState("");
  const [issueId, setIssueId] = useState("");

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    start.mutate(
      {
        patient_cpid: patientCpid,
        order_id: orderId || undefined,
        issue_id: issueId || undefined,
        started_by: user?.displayName ?? user?.id,
      },
      {
        onSuccess: (episode) => {
          const id = String((episode as { episodeId?: string }).episodeId ?? (episode as { id?: string }).id ?? "");
          if (id) router.push(`/madi/transfusion/${id}`);
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell title="Record Transfusion" subtitle="Start a transfusion episode with observations" icon={<Activity className="h-6 w-6" />}>
        <form onSubmit={handleSubmit} className="max-w-lg space-y-4 rounded-2xl border border-border bg-card p-6">
          <label className="block text-sm font-medium text-foreground">
            Patient CPID
            <input required value={patientCpid} onChange={(e) => setPatientCpid(e.target.value)} className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm font-mono" />
          </label>
          <label className="block text-sm font-medium text-foreground">
            Order ID (optional)
            <input value={orderId} onChange={(e) => setOrderId(e.target.value)} className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm font-mono" />
          </label>
          <label className="block text-sm font-medium text-foreground">
            Issue ID (optional)
            <input value={issueId} onChange={(e) => setIssueId(e.target.value)} className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm font-mono" />
          </label>
          <button type="submit" disabled={start.isPending} className="inline-flex items-center gap-2 rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50">
            {start.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            Start episode
          </button>
        </form>
        <Link href="/madi/haemovigilance" className="mt-4 inline-block text-sm text-rose-600 hover:underline">
          Report adverse reaction →
        </Link>
      </PageShell>
    </AppLayout>
  );
}
