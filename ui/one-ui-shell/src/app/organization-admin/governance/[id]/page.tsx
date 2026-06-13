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
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" />
            Organisations
          </Link>
        </div>

        {loading ? (
          <div className="text-sm text-muted-foreground">Loading…</div>
        ) : error ? (
          <div className="rounded-md border border-danger/28 bg-danger-soft px-4 py-3 text-sm text-danger">{error}</div>
        ) : summary ? (
          <>
            <div className="flex items-start gap-4">
              <Building2 className="h-10 w-10 text-muted-foreground" />
              <div>
                <p className="text-sm text-muted-foreground">
                  {summary.organisationCode} · {summary.status}
                </p>
              </div>
            </div>

            <dl className="mt-8 grid max-w-xl grid-cols-2 gap-4 text-sm">
              <div className="rounded-lg border border-border bg-card p-4">
                <dt className="text-muted-foreground">Organisation units</dt>
                <dd className="mt-1 text-lg font-semibold text-foreground">{summary.organisationUnitCount ?? 0}</dd>
              </div>
              <div className="rounded-lg border border-border bg-card p-4">
                <dt className="text-muted-foreground">Linked facilities</dt>
                <dd className="mt-1 text-lg font-semibold text-foreground">{summary.linkedFacilityCount ?? 0}</dd>
              </div>
              <div className="rounded-lg border border-border bg-card p-4">
                <dt className="text-muted-foreground">Linked Indawo sites</dt>
                <dd className="mt-1 text-lg font-semibold text-foreground">{summary.linkedSiteCount ?? 0}</dd>
              </div>
              <div className="rounded-lg border border-border bg-card p-4">
                <dt className="text-muted-foreground">Assignments (org-scoped)</dt>
                <dd className="mt-1 text-lg font-semibold text-foreground">{summary.assignmentCount ?? 0}</dd>
              </div>
            </dl>

            <p className="mt-8 max-w-2xl text-xs text-muted-foreground">
              PIC lifecycle remains in Varapi with Tuso mirror; use assignment type{" "}
              <code className="rounded bg-neutral-100 px-1">PRACTITIONER_IN_CHARGE</code> in workforce governance for
              parallel governed scope where required.
            </p>
          </>
        ) : null}
      </PageShell>
    </AppLayout>
  );
}
