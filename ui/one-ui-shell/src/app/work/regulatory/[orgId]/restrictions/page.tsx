"use client";

/**
 * Read-only register-entry restrictions for a regulatory organisation.
 *
 * Route: /work/regulatory/[orgId]/restrictions
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useRegisterEntryRestrictions } from "@/hooks/queries/useRegulatoryDesk";

export default function RegulatoryRestrictionsPage() {
  const params = useParams<{ orgId: string }>();
  const orgId = decodeURIComponent(String(params?.orgId ?? ""));
  const { data, isLoading, isError } = useRegisterEntryRestrictions(orgId);

  return (
    <AppLayout>
      <PageShell
        title="Register restrictions"
        subtitle="Conditions and interdictions currently recorded on register entries"
        serviceSlug="varapi"
      >
        <LuminousStage className="space-y-5 p-5 sm:p-6">
          <Link
            href={`/work/regulatory/${encodeURIComponent(orgId)}`}
            className="inline-flex items-center gap-1 text-xs font-semibold text-teal-700 hover:underline"
          >
            <ArrowLeft className="h-3.5 w-3.5" /> Regulatory workspace
          </Link>

          <p className="text-sm text-muted-foreground">
            New restrictions are imposed through{" "}
            <Link
              href={`/work/regulators/${encodeURIComponent(orgId)}/disciplinary`}
              className="font-semibold text-teal-700 hover:underline"
            >
              disciplinary determination
            </Link>
            . This page is a read-only view of what is already on the register.
          </p>

          {isLoading ? <p className="text-sm text-muted-foreground">Loading restrictions…</p> : null}
          {isError ? (
            <p className="text-sm text-muted-foreground">Restrictions could not be loaded.</p>
          ) : null}
          {!isLoading && (data?.restrictions?.length ?? 0) === 0 ? (
            <p className="text-sm text-muted-foreground">
              {data?.note ?? "No register-entry restrictions for this council."}
            </p>
          ) : null}

          <ul className="space-y-2">
            {(data?.restrictions ?? []).map((r) => (
              <li key={String(r.id)} className="rounded-xl border border-border bg-card p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-sm font-semibold text-foreground">
                    {r.restrictionType ?? "Restriction"}
                  </p>
                  <span className="rounded-full border border-border px-2 py-0.5 text-[11px] text-muted-foreground">
                    {r.status ?? "—"}
                  </span>
                </div>
                <p className="mt-1 text-sm text-foreground">{r.description}</p>
                <p className="mt-2 text-xs text-muted-foreground">
                  Entry {r.registrationNumber ?? r.registrationRecordId ?? "—"}
                  {r.registerCode ? ` · ${r.registerCode}` : ""}
                  {r.validFrom || r.validTo
                    ? ` · ${r.validFrom ?? "…"} → ${r.validTo ?? "…"}`
                    : ""}
                </p>
              </li>
            ))}
          </ul>
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
