"use client";

/**
 * Patient Safety & Pharmacovigilance — provider home.
 * Route: /work/patient-safety
 *
 * Lists the provider's safety reports, links to the new-report flow and the MCAZ workbench, and
 * surfaces the honest adapter posture + external WHO-UMC VigiMobile / VigiFlow eForm link-outs.
 */

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Plus, ShieldAlert, ExternalLink, ClipboardList, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface ReportSummary {
  id: string;
  referenceCode: string | null;
  reportType: "ADR" | "AEFI";
  reporterKind: string;
  status: string;
  subjectInitials: string | null;
  serious: boolean;
  primaryProductName: string | null;
  primaryReactionTerm: string | null;
  createdAt: string;
}

interface Adapter {
  key: string;
  label: string;
  kind: string;
  enabled: boolean;
  status: string;
}

interface VigiMobileLinks {
  disclaimer: string;
  adr_eform_url: string;
  aefi_eform_url: string;
  mcaz_whatsapp_number: string;
  mcaz_portal_url: string;
}

const STATUS_TONE: Record<string, string> = {
  DRAFT: "bg-neutral-100 text-neutral-700",
  SUBMITTED: "bg-blue-100 text-blue-800",
  UNDER_REVIEW: "bg-amber-100 text-amber-800",
  NEEDS_MORE_INFORMATION: "bg-amber-100 text-amber-800",
  ESCALATED: "bg-red-100 text-red-800",
  CLOSED: "bg-green-100 text-green-800",
};

export default function PatientSafetyHomePage() {
  const { data: reportsData, isLoading } = useQuery<{ data: ReportSummary[] }>({
    queryKey: ["patient-safety-reports", "mine"],
    queryFn: () => apiClient.get(`/internal/v1/patient-safety/reports`),
  });

  const { data: linksData } = useQuery<{ data: VigiMobileLinks }>({
    queryKey: ["patient-safety-vigimobile-links"],
    queryFn: () => apiClient.get(`/internal/v1/patient-safety/config/vigimobile-links`),
  });

  const { data: adaptersData } = useQuery<{ data: Adapter[] }>({
    queryKey: ["patient-safety-adapters"],
    queryFn: () => apiClient.get(`/internal/v1/patient-safety/config/adapters`),
  });

  const reports = reportsData?.data ?? [];
  const links = linksData?.data;
  const adapters = adaptersData?.data ?? [];
  const vigiflow = adapters.find((a) => a.key === "vigiflow-e2b");

  return (
    <AppLayout>
      <PageShell
        title="Patient Safety & Pharmacovigilance"
        subtitle="Report adverse drug reactions (ADR) and adverse events following immunisation (AEFI)"
      >
        <div className="mb-6 flex flex-wrap items-center gap-3">
          <Link
            href="/work/patient-safety/new"
            className="inline-flex items-center gap-1.5 px-4 py-2 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover"
          >
            <Plus className="w-4 h-4" /> New safety report
          </Link>
          <Link
            href="/work/patient-safety/mcaz"
            className="inline-flex items-center gap-1.5 px-4 py-2 border border-border text-sm font-medium rounded-lg hover:bg-muted"
          >
            <ClipboardList className="w-4 h-4" /> MCAZ workbench
          </Link>
        </div>

        {/* Honest adapter posture banner */}
        {vigiflow && !vigiflow.enabled && (
          <div className="mb-6 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
            <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
            <span>
              <strong>Manual entry mode.</strong> The VigiFlow E2B(R3) submission adapter is not configured. Cases are
              prepared as E2B(R3)-aligned export packages and entered into VigiFlow manually by MCAZ — Impilo never
              reports a case as &ldquo;submitted&rdquo; until an adapter is connected.
            </span>
          </div>
        )}

        <div className="grid gap-6 lg:grid-cols-3">
          {/* Reports list */}
          <section className="lg:col-span-2">
            <h2 className="mb-3 text-sm font-semibold text-muted-foreground">My safety reports</h2>
            {isLoading ? (
              <div className="flex items-center gap-2 py-12 text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading reports…
              </div>
            ) : reports.length === 0 ? (
              <div className="rounded-lg border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
                No safety reports yet. Start one from a clinical or vaccination encounter.
              </div>
            ) : (
              <ul className="space-y-2">
                {reports.map((r) => (
                  <li key={r.id}>
                    <Link
                      href={`/work/patient-safety/reports/${r.id}`}
                      className="block rounded-lg border border-border p-3 hover:bg-muted"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium">
                          {r.referenceCode ?? "Draft"} · {r.reportType}
                          {r.serious && (
                            <span className="ml-2 rounded bg-red-100 px-1.5 py-0.5 text-xs font-semibold text-red-800">
                              Serious
                            </span>
                          )}
                        </span>
                        <span
                          className={`rounded px-2 py-0.5 text-xs font-medium ${
                            STATUS_TONE[r.status] ?? "bg-neutral-100 text-neutral-700"
                          }`}
                        >
                          {r.status.replace(/_/g, " ")}
                        </span>
                      </div>
                      <div className="mt-1 text-sm text-muted-foreground">
                        {r.primaryProductName ?? "—"}
                        {r.primaryReactionTerm ? ` → ${r.primaryReactionTerm}` : ""}
                        {r.subjectInitials ? ` · ${r.subjectInitials}` : ""}
                      </div>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {/* VigiMobile external link-outs */}
          <aside>
            <h2 className="mb-3 text-sm font-semibold text-muted-foreground">WHO-UMC VigiMobile (external)</h2>
            {links && (
              <div className="space-y-2 rounded-lg border border-border p-4 text-sm">
                <p className="text-xs text-muted-foreground">{links.disclaimer}</p>
                <a
                  href={links.adr_eform_url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center gap-1.5 text-primary hover:underline"
                >
                  <ExternalLink className="h-3.5 w-3.5" /> ADR eForm (VigiFlow)
                </a>
                <a
                  href={links.aefi_eform_url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center gap-1.5 text-primary hover:underline"
                >
                  <ExternalLink className="h-3.5 w-3.5" /> AEFI eForm (VigiFlow)
                </a>
                <a
                  href={links.mcaz_portal_url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center gap-1.5 text-primary hover:underline"
                >
                  <ExternalLink className="h-3.5 w-3.5" /> MCAZ pharmacovigilance
                </a>
                <p className="pt-1 text-xs text-muted-foreground">
                  MCAZ WhatsApp PV channel: <span className="font-medium">{links.mcaz_whatsapp_number}</span>
                </p>
              </div>
            )}
          </aside>
        </div>
      </PageShell>
    </AppLayout>
  );
}
