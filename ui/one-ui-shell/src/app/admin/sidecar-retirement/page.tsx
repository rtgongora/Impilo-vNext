"use client";

import Link from "next/link";
import { ArrowLeft, ShieldAlert } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  SIDECAR_RETIREMENT_LEDGER,
  type SidecarRetirementStatus,
} from "@/lib/sidecar-retirement-ledger-v2";

const STATUS_ORDER: SidecarRetirementStatus[] = [
  "absorbed into Experience",
  "partially absorbed into Experience",
  "retired sidecar path",
  "blocked by missing backend contract",
];

const STATUS_STYLES: Record<SidecarRetirementStatus, string> = {
  "absorbed into Experience": "border-emerald-200 bg-emerald-50 text-emerald-900",
  "partially absorbed into Experience": "border-cyan-200 bg-cyan-50 text-cyan-950",
  "retired sidecar path": "border-slate-200 bg-slate-50 text-slate-900",
  "blocked by missing backend contract": "border-amber-200 bg-amber-50 text-amber-950",
};

export default function SidecarRetirementPage() {
  return (
    <AppLayout>
      <PageShell
        title="Sidecar Retirement Ledger"
        subtitle="Canonical Experience replacements and the exact contract blockers for sidecars that are not yet safe to retire."
      >
        <div className="space-y-8">
          <div className="flex flex-wrap items-center gap-3">
            <Link
              href="/admin/integration-status"
              className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
            >
              <ArrowLeft className="h-4 w-4" />
              Integration status
            </Link>
            <Link
              href="/finance/commerce-integrations"
              className="inline-flex items-center gap-1 text-sm text-impilo-600 hover:text-impilo-700"
            >
              Commerce & payer stack
            </Link>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-5 text-sm text-slate-700 shadow-sm">
            <div className="flex items-start gap-3">
              <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-slate-500" />
              <div>
                <p className="font-semibold text-slate-900">Acceptance rule</p>
                <p className="mt-1">
                  Impilo delivers one sign-in and one Experience layer. A sidecar is only retired when the operator can
                  complete the same real task in Experience or the ledger explicitly records the backend contract still
                  missing.
                </p>
              </div>
            </div>
          </div>

          {STATUS_ORDER.map((status) => {
            const entries = SIDECAR_RETIREMENT_LEDGER.filter((entry) => entry.status === status);

            return (
              <section key={status} className="space-y-3">
                <div className={`rounded-xl border px-4 py-3 ${STATUS_STYLES[status]}`}>
                  <h2 className="text-sm font-semibold uppercase tracking-[0.18em]">{status}</h2>
                  <p className="mt-1 text-sm normal-case">
                    {status === "absorbed into Experience"
                      ? "These capabilities have an inspected Experience entry point and should not be accepted through the sidecar."
                      : status === "partially absorbed into Experience"
                        ? "These capabilities have real Experience entry points, but some parity or canonical route work is still incomplete."
                      : status === "retired sidecar path"
                        ? "These sidecar entry points are superseded by an existing Experience route in the accepted shell."
                        : "These flows remain blocked until the stated Experience BFF, gateway, or canonical route contract exists."}
                  </p>
                </div>

                <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white shadow-sm">
                  <table className="w-full text-sm">
                    <thead className="bg-slate-50 text-left">
                      <tr>
                        <th className="px-4 py-3 font-medium text-slate-700">Capability</th>
                        <th className="px-4 py-3 font-medium text-slate-700">Old UI path</th>
                        <th className="px-4 py-3 font-medium text-slate-700">Experience path</th>
                        <th className="px-4 py-3 font-medium text-slate-700">Contract blocker</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {entries.map((entry) => (
                        <tr key={`${entry.oldUiPath}-${entry.capability}`}>
                          <td className="px-4 py-3 align-top">
                            <p className="font-medium text-slate-900">{entry.capability}</p>
                            <p className="mt-1 text-xs text-slate-500">{entry.notes}</p>
                          </td>
                          <td className="px-4 py-3 align-top font-mono text-xs text-slate-600">{entry.oldUiPath}</td>
                          <td className="px-4 py-3 align-top font-mono text-xs text-slate-600">{entry.newExperiencePath}</td>
                          <td className="px-4 py-3 align-top text-xs text-slate-600">
                            {entry.blockerContract ?? "None - Experience route is real and accepted."}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            );
          })}
        </div>
      </PageShell>
    </AppLayout>
  );
}
