"use client";

import Link from "next/link";
import { AlertCircle, ArrowRight, CreditCard, GitMerge, ShieldCheck, UserCheck, Users } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useClientStewardshipWorkspace } from "@/hooks/queries/useClientRegistry";

function labelize(value: string | null | undefined) {
  if (!value) return "Not stated";
  return value.replaceAll("_", " ");
}

export default function VitoOpsPage() {
  const workspace = useClientStewardshipWorkspace();
  const data = workspace.data?.data;

  const sections = [
    {
      label: "Deduplication Queue",
      description: "Review and resolve potential duplicate person records before merge execution.",
      Icon: GitMerge,
      tone: "bg-amber-50 text-amber-700 border-amber-100",
      count: data?.duplicateQueue.length ?? 0,
    },
    {
      label: "Verification Queue",
      description: "Identity verification and assurance decisions awaiting stewardship action.",
      Icon: ShieldCheck,
      tone: "bg-sky-50 text-sky-700 border-sky-100",
      count: data?.verificationQueue.length ?? 0,
    },
    {
      label: "Stewardship Actions",
      description: "Open quality, correction, guardian, and authorisation follow-up work.",
      Icon: UserCheck,
      tone: "bg-emerald-50 text-emerald-700 border-emerald-100",
      count: data?.stewardshipQueue.length ?? 0,
    },
    {
      label: "Identifier Issuance",
      description: "Operational visibility over provisional and canonical Impilo identifier progression.",
      Icon: CreditCard,
      tone: "bg-slate-50 text-slate-700 border-slate-100",
      count: data?.verificationQueue.filter((item) => item.decision === "VERIFIED").length ?? 0,
    },
  ];

  return (
    <AppLayout>
      <PageShell
        title="Identity Operations"
        subtitle="Stewardship queues for duplicate review, verification, identifier issuance, and registry exceptions"
        icon={<Users className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-gray-200 bg-white p-4">
            <div>
              <p className="text-sm font-medium text-gray-900">Identity stewardship console</p>
              <p className="mt-1 text-sm text-gray-500">
                Use this workspace to resolve verification uncertainty, confirm duplicates, and advance registry quality.
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Link
                href="/operations/vito/biometrics"
                className="inline-flex items-center gap-2 rounded-xl border border-impilo-200 bg-impilo-50 px-4 py-2 text-sm font-medium text-impilo-900 hover:border-impilo-300"
              >
                Biometric governance
                <ArrowRight className="h-4 w-4" />
              </Link>
              <Link
                href="/registry/clients"
                className="inline-flex items-center gap-2 rounded-xl border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:border-slate-400 hover:text-slate-900"
              >
                Client registry
                <ArrowRight className="h-4 w-4" />
              </Link>
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {sections.map(({ label, description, Icon, tone, count }) => (
              <div key={label} className={`rounded-2xl border bg-white p-5 ${tone}`}>
                <div className="flex items-center gap-3">
                  <div className="rounded-lg bg-white p-2">
                    <Icon className="h-5 w-5" />
                  </div>
                  <div>
                    <h3 className="font-semibold text-gray-900">{label}</h3>
                    <span className="rounded bg-white px-2 py-0.5 text-xs text-gray-500">{count} pending</span>
                  </div>
                </div>
                <p className="mt-3 text-sm text-gray-600">{description}</p>
              </div>
            ))}
          </div>

          <div className="grid gap-4 lg:grid-cols-3">
            <div className="rounded-2xl border border-gray-200 bg-white p-5">
              <div className="flex items-center gap-2">
                <GitMerge className="h-4 w-4 text-amber-600" />
                <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Duplicate Review</h3>
              </div>
              <div className="mt-4 space-y-3">
                {data?.duplicateQueue.length ? (
                  data.duplicateQueue.slice(0, 6).map((item) => (
                    <Link key={item.matchId} href={`/registry/clients/${item.sourceHealthId}`} className="block rounded-2xl border border-gray-200 p-4 hover:border-slate-400">
                      <p className="font-medium text-gray-900">
                        {item.sourceHealthId.slice(0, 8)} ↔ {item.candidateHealthId.slice(0, 8)}
                      </p>
                      <p className="mt-1 text-xs text-gray-500">
                        Score {item.matchScore} • {labelize(item.status)}
                      </p>
                    </Link>
                  ))
                ) : (
                  <EmptyQueue label="No duplicate candidates are waiting right now." />
                )}
              </div>
            </div>

            <div className="rounded-2xl border border-gray-200 bg-white p-5">
              <div className="flex items-center gap-2">
                <ShieldCheck className="h-4 w-4 text-sky-600" />
                <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Verification Queue</h3>
              </div>
              <div className="mt-4 space-y-3">
                {data?.verificationQueue.length ? (
                  data.verificationQueue.slice(0, 6).map((item) => (
                    <div key={item.reviewId} className="rounded-2xl border border-gray-200 p-4">
                      <p className="font-medium text-gray-900">{labelize(item.reviewType)}</p>
                      <p className="mt-1 text-xs text-gray-500">{labelize(item.status)}</p>
                      <p className="mt-2 text-sm text-gray-600">{item.notes ?? "Verification work item"}</p>
                    </div>
                  ))
                ) : (
                  <EmptyQueue label="No verification reviews are open." />
                )}
              </div>
            </div>

            <div className="rounded-2xl border border-gray-200 bg-white p-5">
              <div className="flex items-center gap-2">
                <AlertCircle className="h-4 w-4 text-rose-600" />
                <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Stewardship Actions</h3>
              </div>
              <div className="mt-4 space-y-3">
                {data?.stewardshipQueue.length ? (
                  data.stewardshipQueue.slice(0, 6).map((item) => (
                    <div key={item.actionId} className="rounded-2xl border border-gray-200 p-4">
                      <p className="font-medium text-gray-900">{labelize(item.actionType)}</p>
                      <p className="mt-1 text-xs text-gray-500">
                        {labelize(item.status)} • {item.owner ?? "Unassigned"}
                      </p>
                      <p className="mt-2 text-sm text-gray-600">{item.completionNotes ?? "Operational follow-up required."}</p>
                    </div>
                  ))
                ) : (
                  <EmptyQueue label="No stewardship actions are open." />
                )}
              </div>
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}

function EmptyQueue({ label }: { label: string }) {
  return <div className="rounded-2xl bg-gray-50 p-4 text-sm text-gray-500">{label}</div>;
}
