"use client";

/**
 * Organisation & workforce governance registry (Experience → BFF → workforce-governance-service).
 * Route: /organization-admin/governance | ORGANIZATION_ADMIN
 */

import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowLeft, Building2, Network } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

type OrgRow = {
  id?: string;
  organisationCode?: string;
  name?: string;
  status?: string;
};

type GovernanceListResponse = {
  success?: boolean;
  data?: OrgRow[];
  error?: string;
};

export default function OrganisationGovernancePage() {
  const [rows, setRows] = useState<OrgRow[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await apiClient.get<GovernanceListResponse>("/internal/v1/workforce-governance/organisations");
        if (cancelled) return;
        if (res && res.success === false) {
          setError("Unable to load organisations");
          setRows([]);
        } else {
          setRows(Array.isArray(res?.data) ? res.data : []);
        }
      } catch (e: unknown) {
        const msg = e && typeof e === "object" && "error" in e ? String((e as { error?: { message?: string } }).error?.message) : "Request failed";
        if (!cancelled) {
          setError(msg);
          setRows([]);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <AppLayout>
      <PageShell
        title="Organisations & governance"
        subtitle="Legal/operating entities, facility and site linkages, jurisdictions, role definitions, and assignment scope used by Tshepo for facility-aware authorization."
      >
        <OrganizationPlaneContextBar />
        <div className="mb-6 flex items-center gap-3">
          <Link
            href="/organization-admin"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" />
            Organisation admin
          </Link>
        </div>
        <div className="flex items-start justify-end gap-4">
          <Network className="h-10 w-10 text-muted-foreground" aria-hidden />
        </div>

        {error && (
          <div className="mt-6 rounded-md border border-warning/35 bg-warning-soft px-4 py-3 text-sm text-warning-foreground">
            {error} — ensure workforce-governance-service is running and{" "}
            <code className="rounded bg-amber-100 px-1">impilo.services.workforce-governance-base-url</code> is set on the BFF.
          </div>
        )}

        <div className="mt-8 overflow-hidden rounded-lg border border-border bg-card shadow-sm">
          <div className="border-b border-border bg-background px-4 py-3 text-sm font-medium text-foreground">
            Organisations
          </div>
          {loading ? (
            <div className="px-4 py-8 text-sm text-muted-foreground">Loading…</div>
          ) : rows.length === 0 ? (
            <div className="px-4 py-8 text-sm text-muted-foreground">No organisations for this tenant yet.</div>
          ) : (
            <ul className="divide-y divide-slate-100">
              {rows.map((o) => (
                <li key={o.id ?? o.organisationCode} className="flex items-center justify-between gap-4 px-4 py-3">
                  <div className="flex items-center gap-3">
                    <Building2 className="h-5 w-5 text-muted-foreground" />
                    <div>
                      <div className="font-medium text-foreground">{o.name ?? "—"}</div>
                      <div className="text-xs text-muted-foreground">
                        {o.organisationCode ?? "—"} · {o.status ?? "—"}
                      </div>
                    </div>
                  </div>
                  {o.id && (
                    <Link
                      href={`/organization-admin/governance/${o.id}`}
                      className="text-sm font-medium text-indigo-600 hover:text-primary-hover"
                    >
                      View
                    </Link>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
