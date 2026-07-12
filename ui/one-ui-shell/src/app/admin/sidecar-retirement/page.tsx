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
  "retired — deleted 2026-07-12",
  "absorbed into Experience",
  "partially absorbed into Experience",
  "retired sidecar path",
  "blocked by missing backend contract",
];

const STATUS_STYLES: Record<SidecarRetirementStatus, string> = {
  "retired — deleted 2026-07-12": "border-success/25 bg-success-soft text-primary-hover",
  "absorbed into Experience": "border-success/25 bg-success-soft text-primary-hover",
  "partially absorbed into Experience": "border-cyan-200 bg-cyan-50 text-cyan-950",
  "retired sidecar path": "border-border bg-background text-foreground",
  "blocked by missing backend contract": "border-warning/35 bg-warning-soft text-warning-foreground",
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
              className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
            >
              <ArrowLeft className="h-4 w-4" />
              Integration status
            </Link>
            <Link
              href="/finance/commerce-integrations"
              className="inline-flex items-center gap-1 text-sm text-primary hover:text-primary-hover"
            >
              Commerce & payer stack
            </Link>
          </div>

          <div className="rounded-2xl border border-border bg-card p-5 text-sm text-foreground shadow-sm">
            <div className="flex items-start gap-3">
              <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-muted-foreground" />
              <div>
                <p className="font-semibold text-foreground">Acceptance rule</p>
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
                    {status === "retired — deleted 2026-07-12"
                      ? "These sidecar app directories were deleted from the repo; the capability lives only in the Experience shell."
                      : status === "absorbed into Experience"
                      ? "These capabilities have an inspected Experience entry point and should not be accepted through the sidecar."
                      : status === "partially absorbed into Experience"
                        ? "These capabilities have real Experience entry points, but some parity or canonical route work is still incomplete."
                      : status === "retired sidecar path"
                        ? "These sidecar entry points are superseded by an existing Experience route in the accepted shell."
                        : "These flows remain blocked until the stated Experience BFF, gateway, or canonical route contract exists."}
                  </p>
                </div>

                <div className="overflow-x-auto rounded-2xl border border-border bg-card shadow-sm">
                  <table className="w-full text-sm">
                    <thead className="bg-background text-left">
                      <tr>
                        <th className="px-4 py-3 font-medium text-foreground">Capability</th>
                        <th className="px-4 py-3 font-medium text-foreground">Old UI path</th>
                        <th className="px-4 py-3 font-medium text-foreground">Experience path</th>
                        <th className="px-4 py-3 font-medium text-foreground">Contract blocker</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {entries.map((entry) => (
                        <tr key={`${entry.oldUiPath}-${entry.capability}`}>
                          <td className="px-4 py-3 align-top">
                            <p className="font-medium text-foreground">{entry.capability}</p>
                            <p className="mt-1 text-xs text-muted-foreground">{entry.notes}</p>
                          </td>
                          <td className="px-4 py-3 align-top font-mono text-xs text-muted-foreground">{entry.oldUiPath}</td>
                          <td className="px-4 py-3 align-top font-mono text-xs text-muted-foreground">{entry.newExperiencePath}</td>
                          <td className="px-4 py-3 align-top text-xs text-muted-foreground">
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
