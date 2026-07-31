"use client";

/**
 * Regulator queue of student registration applications (NCZ-W1C).
 *
 * Route: /work/regulatory/[orgId]/student-applications
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, GraduationCap } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useRegulatoryOrganisations } from "@/hooks/queries/useRegulatoryOrganisations";
import { useStudentApplications } from "@/hooks/queries/useStudentRegistration";

function fmt(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "—" : d.toLocaleString();
}

export default function StudentApplicationsQueuePage() {
  const params = useParams<{ orgId: string }>();
  const orgId = decodeURIComponent(String(params?.orgId ?? ""));
  const { organisation } = useRegulatoryOrganisations();
  const org = organisation(orgId);
  const { data, isLoading, isError } = useStudentApplications({ organizationId: orgId });

  return (
    <AppLayout>
      <PageShell
        title="Student applications"
        subtitle={org ? `${org.name} — student registration queue` : "Student registration queue"}
        serviceSlug="varapi"
      >
        <LuminousStage className="space-y-5 p-5 sm:p-6">
          <Link
            href={`/work/regulatory/${encodeURIComponent(orgId)}`}
            className="inline-flex items-center gap-1 text-xs font-semibold text-teal-700 hover:underline"
          >
            <ArrowLeft className="h-3.5 w-3.5" /> Regulatory workspace
          </Link>

          {isLoading ? <p className="text-sm text-muted-foreground">Loading applications…</p> : null}
          {isError ? (
            <p className="text-sm text-muted-foreground">
              The student application queue could not be loaded just now.
            </p>
          ) : null}

          {(data ?? []).length === 0 && !isLoading && !isError ? (
            <p className="text-sm text-muted-foreground">
              No student registration applications for this council yet.
            </p>
          ) : null}

          <ul className="space-y-2">
            {(data ?? []).map((app) => (
              <li key={app.id}>
                <Link
                  href={`/work/regulatory/${encodeURIComponent(orgId)}/student-applications/${app.id}`}
                  className="flex items-start justify-between gap-3 rounded-xl border border-border bg-card p-4 transition hover:border-teal-300"
                >
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <GraduationCap className="h-4 w-4 text-muted-foreground" aria-hidden />
                      <span className="text-sm font-semibold text-foreground">
                        Application {app.id}
                      </span>
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground">
                      Provider {app.providerPublicId ?? app.providerId ?? "—"} · submitted{" "}
                      {fmt(app.submittedAt)}
                    </p>
                  </div>
                  <span className="shrink-0 rounded-full border border-border px-2 py-0.5 text-[11px] text-muted-foreground">
                    {(app.workflowState ?? "unknown").toLowerCase().replace(/_/g, " ")}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
