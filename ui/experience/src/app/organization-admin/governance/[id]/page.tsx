"use client";

/**
 * Organisation governance summary — counts and cross-links (BFF aggregated view).
 */

import Link from "next/link";
import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { ArrowLeft, Building2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

type Summary = {
  organisationId?: string;
  organisationCode?: string;
  name?: string;
  status?: string;
  organisationUnitCount?: number;
  linkedFacilityCount?: number;
  linkedSiteCount?: number;
  assignmentCount?: number;
};

type SummaryResponse = {
  success?: boolean;
  data?: Summary;
  error?: string;
};

export default function OrganisationGovernanceDetailPage() {
  const params = useParams();
  const id = typeof params.id === "string" ? params.id : "";
  const [summary, setSummary] = useState<Summary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    (async () => {
      try {
        const res = await apiClient.get<SummaryResponse>(
          `/internal/v1/workforce-governance/organisations/${encodeURIComponent(id)}/summary`,
        );
        if (cancelled) return;
        if (res?.success === false || !res?.data) {
          setError("Summary not available");
          setSummary(null);
        } else {
          setSummary(res.data);
        }
      } catch {
        if (!cancelled) {
          setError("Failed to load summary");
          setSummary(null);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [id]);

  return (
    <AppLayout>
      <PageShell
        title={summary?.name ?? "Organisation"}
        subtitle="Governance summary — linked facilities, sites, and assignments"
      >
        <OrganizationPlaneContextBar />
        <div className="mb-6 flex items-center gap-3">
          <Link
            href="/organization-admin/governance"
            className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900"
          >
            <ArrowLeft className="h-4 w-4" />
            Organisations
          </Link>
        </div>

        {loading ? (
          <div className="text-sm text-slate-500">Loading…</div>
        ) : error ? (
          <div className="rounded-md border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-900">{error}</div>
        ) : summary ? (
          <>
            <div className="flex items-start gap-4">
              <Building2 className="h-10 w-10 text-slate-400" />
              <div>
                <p className="text-sm text-slate-600">
                  {summary.organisationCode} · {summary.status}
                </p>
              </div>
            </div>

            <dl className="mt-8 grid max-w-xl grid-cols-2 gap-4 text-sm">
              <div className="rounded-lg border border-slate-200 bg-white p-4">
                <dt className="text-slate-500">Organisation units</dt>
                <dd className="mt-1 text-lg font-semibold text-slate-900">{summary.organisationUnitCount ?? 0}</dd>
              </div>
              <div className="rounded-lg border border-slate-200 bg-white p-4">
                <dt className="text-slate-500">Linked facilities</dt>
                <dd className="mt-1 text-lg font-semibold text-slate-900">{summary.linkedFacilityCount ?? 0}</dd>
              </div>
              <div className="rounded-lg border border-slate-200 bg-white p-4">
                <dt className="text-slate-500">Linked Indawo sites</dt>
                <dd className="mt-1 text-lg font-semibold text-slate-900">{summary.linkedSiteCount ?? 0}</dd>
              </div>
              <div className="rounded-lg border border-slate-200 bg-white p-4">
                <dt className="text-slate-500">Assignments (org-scoped)</dt>
                <dd className="mt-1 text-lg font-semibold text-slate-900">{summary.assignmentCount ?? 0}</dd>
              </div>
            </dl>

            <p className="mt-8 max-w-2xl text-xs text-slate-500">
              PIC lifecycle remains in Varapi with Tuso mirror; use assignment type{" "}
              <code className="rounded bg-slate-100 px-1">PRACTITIONER_IN_CHARGE</code> in workforce governance for
              parallel governed scope where required.
            </p>
          </>
        ) : null}
      </PageShell>
    </AppLayout>
  );
}
