"use client";

/**
 * Regulatory workspace picker (ROM-W2). Lists the signed-in person's regulatory appointments and
 * lets them enter an org-scoped workspace — which starts a regulatory work session (no facility,
 * no provider). A person appointed at more than one council sees each as a separate context; strict
 * cross-council isolation means entering one never exposes another.
 *
 * Route: /work/regulatory
 */

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Building2, Loader2, ScrollText, ShieldCheck } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import {
  type RegulatoryAppointment,
  regulatoryOrg,
  roleLabel,
} from "@/lib/regulatory/organisations";

function unwrapAppointments(payload: unknown): RegulatoryAppointment[] {
  const attrs = (payload as { data?: { attributes?: { appointments?: unknown } } })?.data?.attributes;
  const list = attrs?.appointments;
  return Array.isArray(list) ? (list as RegulatoryAppointment[]) : [];
}

export default function RegulatoryWorkspacePickerPage() {
  const router = useRouter();
  const [appointments, setAppointments] = useState<RegulatoryAppointment[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [entering, setEntering] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/work-context/regulatory/appointments");
      setAppointments(unwrapAppointments(res));
    } catch {
      setError("Could not load your regulatory appointments.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const enter = async (a: RegulatoryAppointment) => {
    setEntering(a.id);
    setError(null);
    try {
      await apiClient.post<unknown>("/internal/v1/work-context/session", { organisationId: a.organizationId });
      router.push(`/work/regulatory/${encodeURIComponent(a.organizationId)}`);
    } catch {
      setError("Regulatory workspace access is currently unavailable for that organisation.");
      setEntering(null);
    }
  };

  const active = appointments.filter((a) => a.status === "ACTIVE");
  const pending = appointments.filter((a) => a.status !== "ACTIVE");

  return (
    <AppLayout>
      <PageShell
        title="Regulatory workspaces"
        subtitle="Enter a council or HPA workspace you are appointed to. Each is org-scoped and isolated."
        serviceSlug="varapi"
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
          {loading ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading…
            </div>
          ) : error ? (
            <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</div>
          ) : appointments.length === 0 ? (
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
              You have no regulatory appointments. Appointments are issued by a council registrar or
              HPA and verified before they grant workspace access.
            </div>
          ) : (
            <>
              <section className="grid gap-3 sm:grid-cols-2">
                {active.map((a) => {
                  const org = regulatoryOrg(a.organizationId);
                  return (
                    <div key={a.id} className="rounded-2xl border border-border bg-card p-4">
                      <div className="mb-1 flex items-center gap-2">
                        {org?.kind === "authority" ? (
                          <ShieldCheck className="h-4 w-4 text-teal-600" />
                        ) : (
                          <Building2 className="h-4 w-4 text-teal-600" />
                        )}
                        <span className="text-sm font-semibold text-foreground">
                          {org?.name ?? a.organizationId}
                        </span>
                      </div>
                      <div className="mb-3 text-xs text-muted-foreground">
                        {roleLabel(a.roleCode)}
                        {a.jurisdictionCode && a.jurisdictionCode !== "NATIONAL"
                          ? ` · ${a.jurisdictionCode.replace(/_/g, " ").toLowerCase()}`
                          : ""}
                      </div>
                      <button
                        type="button"
                        onClick={() => enter(a)}
                        disabled={entering != null}
                        className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50"
                      >
                        {entering === a.id && <Loader2 className="h-4 w-4 animate-spin" />}
                        Enter workspace
                      </button>
                    </div>
                  );
                })}
              </section>

              {pending.length > 0 && (
                <section className="space-y-2">
                  <h2 className="flex items-center gap-1.5 text-sm font-semibold text-foreground">
                    <ScrollText className="h-4 w-4" /> Awaiting verification
                  </h2>
                  {pending.map((a) => (
                    <div key={a.id} className="rounded-xl border border-border bg-muted/40 p-3 text-sm">
                      <span className="font-medium text-foreground">
                        {regulatoryOrg(a.organizationId)?.name ?? a.organizationId}
                      </span>
                      <span className="text-muted-foreground">
                        {" "}
                        — {roleLabel(a.roleCode)} · {a.status.replace(/_/g, " ").toLowerCase()}
                      </span>
                    </div>
                  ))}
                </section>
              )}
            </>
          )}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
