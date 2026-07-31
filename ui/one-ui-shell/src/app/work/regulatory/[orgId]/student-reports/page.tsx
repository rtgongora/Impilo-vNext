"use client";

/**
 * Student registration W1D reports for a regulatory organisation.
 *
 * Route: /work/regulatory/[orgId]/student-reports
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useProfessionalRegisters } from "@/hooks/queries/useProfessionalRegisters";
import { useStudentRegistrationReport } from "@/hooks/queries/useStudentRegistration";

function JsonPanel({ title, data, loading }: { title: string; data: unknown; loading: boolean }) {
  return (
    <section className="rounded-xl border border-border bg-card p-4">
      <h2 className="text-sm font-semibold text-foreground">{title}</h2>
      {loading ? (
        <p className="mt-2 text-xs text-muted-foreground">Loading…</p>
      ) : (
        <pre className="mt-2 max-h-80 overflow-auto rounded-lg bg-muted/40 p-3 text-[11px] leading-relaxed text-foreground">
          {JSON.stringify(data ?? {}, null, 2)}
        </pre>
      )}
    </section>
  );
}

export default function StudentReportsPage() {
  const params = useParams<{ orgId: string }>();
  const orgId = decodeURIComponent(String(params?.orgId ?? ""));
  const { data: catalogue } = useProfessionalRegisters(orgId);
  const councilId = catalogue?.councilId ?? undefined;

  const board = useStudentRegistrationReport("regulator-board", { councilId });
  const ageing = useStudentRegistrationReport("ageing", { councilId });
  const returns = useStudentRegistrationReport("returns-by-section", { councilId });
  const institution = useStudentRegistrationReport("institution-board", {
    organisationId: orgId,
  });

  return (
    <AppLayout>
      <PageShell
        title="Student registration reports"
        subtitle="Regulator board, ageing, returns by section, institution board (NCZ-W1D)"
        serviceSlug="varapi"
      >
        <LuminousStage className="space-y-5 p-5 sm:p-6">
          <Link
            href={`/work/regulatory/${encodeURIComponent(orgId)}`}
            className="inline-flex items-center gap-1 text-xs font-semibold text-teal-700 hover:underline"
          >
            <ArrowLeft className="h-3.5 w-3.5" /> Regulatory workspace
          </Link>

          {!catalogue?.linked ? (
            <p className="text-sm text-muted-foreground">
              No council is linked to this organisation, so council-scoped boards cannot run.
              The institution board still uses the organisation id.
            </p>
          ) : (
            <p className="text-xs text-muted-foreground">
              Council {catalogue.councilCode} ({catalogue.councilId})
            </p>
          )}

          <div className="grid gap-4 lg:grid-cols-2">
            <JsonPanel title="Regulator board" data={board.data} loading={board.isLoading} />
            <JsonPanel title="Ageing" data={ageing.data} loading={ageing.isLoading} />
            <JsonPanel title="Returns by section" data={returns.data} loading={returns.isLoading} />
            <JsonPanel
              title="Institution board"
              data={institution.data}
              loading={institution.isLoading}
            />
          </div>
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
