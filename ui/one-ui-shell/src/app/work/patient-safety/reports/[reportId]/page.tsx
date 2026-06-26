"use client";

/**
 * Safety report detail (read-only).
 * Route: /work/patient-safety/reports/[reportId]
 *
 * Shows the captured ADR/AEFI report with its products and reactions, and links through to the
 * regulatory case it opened (where MCAZ review happens).
 */

import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, ArrowRight, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface ReportDetail {
  id: string;
  referenceCode: string | null;
  reportType: string;
  reporterKind: string;
  status: string;
  narrative: string | null;
  serious: boolean;
  seriousnessReasons: string[];
  subjectInitials: string | null;
  subjectCpid: string | null;
  caseId: string | null;
  caseReference: string | null;
  products: { id: string; productName: string; productKind: string; batchNumber: string | null; route: string | null }[];
  events: { id: string; term: string; severity: string | null; outcome: string | null }[];
}

export default function SafetyReportDetailPage() {
  const params = useParams();
  const reportId = params.reportId as string;

  const { data, isLoading } = useQuery<{ data: ReportDetail }>({
    queryKey: ["patient-safety-report", reportId],
    queryFn: () => apiClient.get(`/internal/v1/patient-safety/reports/${reportId}`),
  });

  const r = data?.data;

  return (
    <AppLayout>
      <PageShell title={r?.referenceCode ?? "Safety report"} subtitle="Captured ADR / AEFI report">
        <Link
          href="/work/patient-safety"
          className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" /> Back
        </Link>

        {isLoading || !r ? (
          <div className="flex items-center gap-2 py-12 text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading report…
          </div>
        ) : (
          <div className="max-w-2xl space-y-4">
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className="rounded bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-800">{r.reportType}</span>
              <span className="rounded bg-neutral-100 px-2 py-0.5 text-xs font-medium">{r.status.replace(/_/g, " ")}</span>
              {r.serious && <span className="rounded bg-red-100 px-2 py-0.5 text-xs font-medium text-red-800">Serious</span>}
            </div>

            {r.narrative && <p className="text-sm text-muted-foreground">{r.narrative}</p>}

            <div className="rounded-lg border border-border p-4">
              <h3 className="mb-2 text-sm font-semibold">Implicated products</h3>
              {r.products.map((p) => (
                <p key={p.id} className="text-sm text-muted-foreground">
                  {p.productName} · {p.productKind}
                  {p.batchNumber ? ` · batch ${p.batchNumber}` : ""}
                  {p.route ? ` · ${p.route}` : ""}
                </p>
              ))}
            </div>

            <div className="rounded-lg border border-border p-4">
              <h3 className="mb-2 text-sm font-semibold">Reactions</h3>
              {r.events.length === 0 ? (
                <p className="text-sm text-muted-foreground">None recorded</p>
              ) : (
                r.events.map((e) => (
                  <p key={e.id} className="text-sm text-muted-foreground">
                    {e.term}
                    {e.severity ? ` · ${e.severity}` : ""}
                    {e.outcome ? ` · ${e.outcome.replace(/_/g, " ")}` : ""}
                  </p>
                ))
              )}
            </div>

            {r.caseId && (
              <Link
                href={`/work/patient-safety/cases/${r.caseId}`}
                className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover"
              >
                View MCAZ case {r.caseReference} <ArrowRight className="h-4 w-4" />
              </Link>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
