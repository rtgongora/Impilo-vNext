"use client";

/**
 * Regulatory organisation workspace hub (ROM-W2). The landing for an active org-scoped regulatory
 * session — the entry point to that organisation's operational surfaces (registers, applications,
 * inspections, complaints, dashboards). The individual surfaces are built out in later ROM waves;
 * this hub establishes the org context and links to the existing regulator surfaces.
 *
 * Route: /work/regulatory/[orgId]
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Building2, ShieldCheck } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { regulatoryOrg } from "@/lib/regulatory/organisations";

interface SurfaceLink {
  label: string;
  description: string;
  href: string;
  wave: string;
}

export default function RegulatoryOrgWorkspacePage() {
  const params = useParams<{ orgId: string }>();
  const orgId = decodeURIComponent(String(params?.orgId ?? ""));
  const org = regulatoryOrg(orgId);

  const surfaces: SurfaceLink[] = [
    { label: "Registers", description: "Professional registers, entries, restrictions and good standing.", href: `/work/regulators/${encodeURIComponent(orgId)}/registration-review`, wave: "W3" },
    { label: "Applications", description: "Registration, renewal and scope applications + correspondence.", href: `/work/regulators/${encodeURIComponent(orgId)}/registration-review`, wave: "W4" },
    { label: "CPD", description: "Continuing professional development adjudication.", href: `/work/regulators/${encodeURIComponent(orgId)}/cpd-review`, wave: "W5" },
    { label: "Complaints & discipline", description: "Complaints, investigations and disciplinary proceedings.", href: `/work/regulators/${encodeURIComponent(orgId)}/disciplinary`, wave: "W7" },
    { label: "Restrictions", description: "Conditions, restrictions and interdictions on the register.", href: `/work/regulators/${encodeURIComponent(orgId)}/restrictions`, wave: "W3" },
    { label: "Audit", description: "Who reviewed, changed, approved or accessed each record.", href: `/work/regulators/${encodeURIComponent(orgId)}/audit`, wave: "W9" },
  ];

  return (
    <AppLayout>
      <PageShell
        title={org?.name ?? "Regulatory workspace"}
        subtitle={org ? `${org.code} — org-scoped regulatory operations` : "Org-scoped regulatory operations"}
        serviceSlug="varapi"
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
          <Link
            href="/work/regulatory"
            className="inline-flex items-center gap-1 text-xs font-semibold text-teal-700 hover:underline"
          >
            <ArrowLeft className="h-3.5 w-3.5" /> All regulatory workspaces
          </Link>

          <div className="flex items-center gap-2 rounded-lg border border-teal-200 bg-teal-50 p-3 text-sm text-teal-900">
            {org?.kind === "authority" ? (
              <ShieldCheck className="h-4 w-4 shrink-0" />
            ) : (
              <Building2 className="h-4 w-4 shrink-0" />
            )}
            <span>
              You are working in <b>{org?.name ?? orgId}</b>. Everything here is scoped to this
              organisation — you cannot see another council&apos;s records.
            </span>
          </div>

          <section className="grid gap-3 sm:grid-cols-2">
            {surfaces.map((s) => (
              <Link
                key={s.label}
                href={s.href}
                className="rounded-2xl border border-border bg-card p-4 transition hover:border-teal-300"
              >
                <div className="mb-1 flex items-center justify-between">
                  <span className="text-sm font-semibold text-foreground">{s.label}</span>
                  <span className="rounded-full border border-border px-1.5 py-0.5 text-[10px] text-muted-foreground">
                    {s.wave}
                  </span>
                </div>
                <p className="text-xs text-muted-foreground">{s.description}</p>
              </Link>
            ))}
          </section>
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
