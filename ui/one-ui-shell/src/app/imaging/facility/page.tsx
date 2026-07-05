"use client";

/**
 * Facility imaging dashboard — worklist snapshot, per-facility imaging capability +
 * go-live readiness, modality registry (BFF-redacted for non-technical actors), and
 * the study reconciliation exceptions queue.
 *
 * Honest states: unconfigured capability and empty registries render as explicit
 * "not configured" panels, never as fake readiness.
 */

import { useState } from "react";
import Link from "next/link";
import { AlertTriangle, CheckCircle2, Loader2, ScanLine, XCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useLabWorklist } from "@/hooks/queries/useLabWorklist";
import {
  useFacilityImagingCapability,
  useFacilityImagingModalities,
  useFacilityImagingReadiness,
  useImagingOpsExceptions,
  useResolveImagingException,
  type ImagingStudyException,
} from "@/hooks/queries/useImaging";

interface ReadinessCheck {
  code?: string;
  passed?: boolean;
  required?: boolean;
  evidence?: string;
}

function readinessTone(readiness?: string): string {
  switch (readiness) {
    case "READY":
      return "bg-emerald-100 text-emerald-800";
    case "PARTIAL":
      return "bg-amber-100 text-amber-800";
    case "NO_IMAGING":
      return "bg-neutral-100 text-neutral-700";
    case "NOT_CONFIGURED":
      return "bg-neutral-100 text-neutral-700";
    default:
      return "bg-red-100 text-red-800";
  }
}

function ExceptionRow({ exception }: { exception: ImagingStudyException }) {
  const resolveMut = useResolveImagingException();
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);

  const act = (action: string, dismiss: boolean) => {
    setError(null);
    resolveMut.mutate(
      { exceptionId: exception.id, body: { action, note: note.trim() || undefined, dismiss } },
      {
        onError: (err) =>
          setError(err instanceof Error && err.message ? err.message : "Could not resolve exception."),
      },
    );
  };

  return (
    <li className="py-3 text-sm" data-testid="imaging-exception-row">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-800">
            {exception.exceptionType ?? "EXCEPTION"}
          </span>
          <span className="ml-2 font-mono text-xs text-muted-foreground">
            study #{exception.studyId ?? "—"} · #{exception.id}
          </span>
        </div>
        <span className="text-xs text-muted-foreground">
          {exception.createdAt ? new Date(String(exception.createdAt)).toLocaleString() : ""}
        </span>
      </div>
      {exception.detail ? <p className="mt-1 text-muted-foreground">{exception.detail}</p> : null}
      <div className="mt-2 flex flex-wrap items-center gap-2">
        <input
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Resolution note (audited)"
          aria-label={`Resolution note for exception ${exception.id}`}
          className="impilo-pill-input w-64 text-xs"
        />
        <button
          type="button"
          onClick={() => act("RESOLVED_MANUAL_REVIEW", false)}
          disabled={resolveMut.isPending}
          className="rounded-md bg-primary px-3 py-1 text-xs font-medium text-primary-foreground disabled:opacity-50"
        >
          {resolveMut.isPending ? "Saving…" : "Resolve"}
        </button>
        <button
          type="button"
          onClick={() => act("DISMISSED", true)}
          disabled={resolveMut.isPending}
          className="rounded-md border border-border px-3 py-1 text-xs font-medium text-muted-foreground disabled:opacity-50"
        >
          Dismiss
        </button>
      </div>
      {error ? <p className="mt-1 text-xs text-red-600">{error}</p> : null}
    </li>
  );
}

export default function FacilityImagingDashboardPage() {
  const facility = useFacilityStore((s) => s.facility);
  const worklistQ = useLabWorklist({
    facilityId: facility?.id,
    type: "IMAGING",
    size: 10,
  });
  const capabilityQ = useFacilityImagingCapability(facility?.id);
  const readinessQ = useFacilityImagingReadiness(facility?.id);
  const modalitiesQ = useFacilityImagingModalities(facility?.id, true);
  const exceptionsQ = useImagingOpsExceptions("OPEN");

  const summary = worklistQ.data?.data?.summary;
  const items = worklistQ.data?.data?.items ?? [];
  const capability = capabilityQ.data?.data;
  const readiness = readinessQ.data?.data as
    | { readiness?: string; configured?: boolean; deploymentMode?: string; activeModalities?: number; checks?: ReadinessCheck[] }
    | undefined;
  const modalities = modalitiesQ.data?.data ?? [];
  const exceptions = exceptionsQ.data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Facility imaging dashboard"
        subtitle="PACS worklist snapshot, capability readiness, modality registry, and reconciliation queue for the active facility"
        icon={<ScanLine className="h-6 w-6" />}
      >
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <FeatureMaturityBadge status="live" detail="OROS imaging worklist + PACS capability model" />
          <Link href="/imaging/worklist" className="text-sm font-medium text-primary hover:underline">
            Open full worklist →
          </Link>
        </div>

        {!facility?.id ? (
          <div className="rounded-lg border border-warning/35 bg-warning-soft p-6 text-sm text-warning-foreground">
            Select a facility to load imaging operations.
          </div>
        ) : (
          <div className="space-y-6">
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              {[
                { label: "Pending acquisition", value: summary?.pending_collection ?? 0 },
                { label: "In progress", value: summary?.in_progress ?? 0 },
                { label: "Completed", value: summary?.completed ?? 0 },
                { label: "Urgent", value: summary?.urgent ?? 0 },
              ].map((card) => (
                <div key={card.label} className="rounded-2xl border border-border bg-card p-4">
                  <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{card.label}</p>
                  <p className="mt-2 text-3xl font-semibold text-foreground">{card.value}</p>
                </div>
              ))}
            </div>

            <section className="rounded-2xl border border-border bg-card p-5" data-testid="imaging-capability-section">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h2 className="text-sm font-semibold text-foreground">Imaging capability &amp; go-live readiness</h2>
                {readiness?.readiness ? (
                  <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${readinessTone(readiness.readiness)}`}>
                    {readiness.readiness}
                  </span>
                ) : null}
              </div>
              {capabilityQ.isLoading || readinessQ.isLoading ? (
                <div className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading capability…
                </div>
              ) : capabilityQ.isError || !capability ? (
                <p className="mt-4 text-sm text-muted-foreground">
                  No imaging capability profile is configured for this facility yet. A technical
                  administrator must declare the deployment mode (PACS/VNA, gateway, manual import…)
                  before imaging go-live.
                </p>
              ) : (
                <div className="mt-4 space-y-4">
                  <dl className="grid gap-3 text-sm sm:grid-cols-2 xl:grid-cols-4">
                    <div>
                      <dt className="text-xs uppercase text-muted-foreground">Deployment mode</dt>
                      <dd className="font-medium text-foreground">{String(capability.deploymentMode ?? "—")}</dd>
                    </div>
                    <div>
                      <dt className="text-xs uppercase text-muted-foreground">Operational status</dt>
                      <dd className="font-medium text-foreground">{String(capability.operationalStatus ?? "—")}</dd>
                    </div>
                    <div>
                      <dt className="text-xs uppercase text-muted-foreground">Active modalities</dt>
                      <dd className="font-medium text-foreground">{readiness?.activeModalities ?? modalities.length}</dd>
                    </div>
                    <div>
                      <dt className="text-xs uppercase text-muted-foreground">Supported modalities</dt>
                      <dd className="font-medium text-foreground">{String(capability.supportedModalities ?? "—")}</dd>
                    </div>
                  </dl>
                  {Array.isArray(readiness?.checks) && readiness.checks.length > 0 ? (
                    <ul className="grid gap-2 sm:grid-cols-2">
                      {readiness.checks.map((check) => (
                        <li key={String(check.code)} className="flex items-start gap-2 text-sm">
                          {check.passed ? (
                            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
                          ) : check.required ? (
                            <XCircle className="mt-0.5 h-4 w-4 shrink-0 text-red-600" />
                          ) : (
                            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
                          )}
                          <span>
                            <span className="font-medium text-foreground">{check.code}</span>
                            {check.evidence ? (
                              <span className="block text-xs text-muted-foreground">{check.evidence}</span>
                            ) : null}
                          </span>
                        </li>
                      ))}
                    </ul>
                  ) : null}
                </div>
              )}
            </section>

            <section className="rounded-2xl border border-border bg-card p-5" data-testid="imaging-modality-section">
              <h2 className="text-sm font-semibold text-foreground">Modality registry</h2>
              {modalitiesQ.isLoading ? (
                <div className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading modalities…
                </div>
              ) : modalitiesQ.isError ? (
                <p className="mt-4 text-sm text-muted-foreground">Modality registry is unavailable for this facility.</p>
              ) : modalities.length === 0 ? (
                <p className="mt-4 text-sm text-muted-foreground">
                  No modalities registered. Machines are registered by technical administrators as
                  part of facility imaging go-live.
                </p>
              ) : (
                <div className="mt-4 overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="text-left text-xs uppercase text-muted-foreground">
                      <tr>
                        <th className="py-2 pr-4">Type</th>
                        <th className="py-2 pr-4">Name</th>
                        <th className="py-2 pr-4">Vendor / model</th>
                        <th className="py-2 pr-4">Status</th>
                        <th className="py-2 pr-4">Worklist</th>
                        <th className="py-2 pr-4">DICOM store</th>
                        <th className="py-2">AE title</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {modalities.map((m) => (
                        <tr key={String(m.id)}>
                          <td className="py-2 pr-4 font-medium">{m.modalityType ?? "—"}</td>
                          <td className="py-2 pr-4">{m.name ?? "—"}</td>
                          <td className="py-2 pr-4 text-muted-foreground">
                            {[m.vendor, m.model].filter(Boolean).join(" ") || "—"}
                          </td>
                          <td className="py-2 pr-4">
                            <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs">
                              {m.operationalStatus ?? "UNKNOWN"}
                            </span>
                          </td>
                          <td className="py-2 pr-4">{m.supportsWorklist ? "Yes" : "No"}</td>
                          <td className="py-2 pr-4">{m.supportsDicomStorage ? "Yes" : "No"}</td>
                          <td className="py-2 font-mono text-xs">{m.aeTitle ?? "restricted"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            <section className="rounded-2xl border border-border bg-card p-5" data-testid="imaging-exceptions-section">
              <h2 className="text-sm font-semibold text-foreground">Study reconciliation queue (open exceptions)</h2>
              {exceptionsQ.isLoading ? (
                <div className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading exceptions…
                </div>
              ) : exceptionsQ.isError ? (
                <p className="mt-4 text-sm text-muted-foreground">The reconciliation queue is unavailable.</p>
              ) : exceptions.length === 0 ? (
                <p className="mt-4 text-sm text-muted-foreground">
                  No open exceptions. Studies without orders, missing accession numbers, or manual
                  imports appear here for auditable resolution.
                </p>
              ) : (
                <ul className="mt-2 divide-y divide-gray-100">
                  {exceptions.map((ex) => (
                    <ExceptionRow key={ex.id} exception={ex} />
                  ))}
                </ul>
              )}
            </section>

            <section className="rounded-2xl border border-border bg-card p-5">
              <h2 className="text-sm font-semibold text-foreground">Recent imaging orders</h2>
              {worklistQ.isLoading ? (
                <div className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading…
                </div>
              ) : worklistQ.isError ? (
                <p className="mt-4 text-sm text-red-600">Could not load imaging worklist.</p>
              ) : items.length === 0 ? (
                <p className="mt-4 text-sm text-muted-foreground">No imaging orders in scope.</p>
              ) : (
                <ul className="mt-4 divide-y divide-slate-100">
                  {items.map((item) => (
                    <li key={String(item.orderId ?? item.id)} className="flex items-center justify-between py-2 text-sm">
                      <span className="font-mono text-foreground">{String(item.orderId ?? item.id)}</span>
                      <span className="text-muted-foreground">{item.patientCpid ?? "—"}</span>
                      <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs">{item.status ?? "—"}</span>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
