"use client";

/**
 * Organisation-scoped configuration audit for a regulatory workspace.
 *
 * Route: /work/regulatory/[orgId]/audit
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useRegulatoryAuditEvents } from "@/hooks/queries/useRegulatoryDesk";

function fmt(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "—" : d.toLocaleString();
}

export default function RegulatoryAuditPage() {
  const params = useParams<{ orgId: string }>();
  const orgId = decodeURIComponent(String(params?.orgId ?? ""));
  const { data, isLoading, isError } = useRegulatoryAuditEvents(orgId);

  return (
    <AppLayout>
      <PageShell
        title="Regulatory audit"
        subtitle="Configuration pack and release acts for this organisation"
        serviceSlug="organization-registry"
      >
        <LuminousStage className="space-y-5 p-5 sm:p-6">
          <Link
            href={`/work/regulatory/${encodeURIComponent(orgId)}`}
            className="inline-flex items-center gap-1 text-xs font-semibold text-teal-700 hover:underline"
          >
            <ArrowLeft className="h-3.5 w-3.5" /> Regulatory workspace
          </Link>

          <p className="text-sm text-muted-foreground">
            {data?.note ??
              "This trail covers configuration pack and release acts for the organisation. It is not a full register-access desk."}
          </p>

          {isLoading ? <p className="text-sm text-muted-foreground">Loading audit events…</p> : null}
          {isError ? (
            <p className="text-sm text-muted-foreground">Audit events could not be loaded.</p>
          ) : null}
          {!isLoading && (data?.events?.length ?? 0) === 0 ? (
            <p className="text-sm text-muted-foreground">No configuration audit events yet.</p>
          ) : null}

          <ul className="space-y-2">
            {(data?.events ?? []).map((e) => (
              <li key={e.id ?? `${e.eventType}-${e.occurredAt}`} className="rounded-xl border border-border bg-card p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-sm font-semibold text-foreground">{e.eventType ?? "Event"}</p>
                  <span className="text-xs text-muted-foreground">{fmt(e.occurredAt)}</span>
                </div>
                <p className="mt-1 text-xs text-muted-foreground">
                  {e.subjectType ?? "subject"}
                  {e.subjectId ? ` · ${e.subjectId}` : ""}
                  {e.actorRole ? ` · ${e.actorRole}` : ""}
                  {e.actorHealthId ? ` · ${e.actorHealthId}` : ""}
                </p>
              </li>
            ))}
          </ul>
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
