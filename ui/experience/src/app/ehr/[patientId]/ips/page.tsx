"use client";

import Link from "next/link";
import { useMemo } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { FileJson, Globe2, Loader2, ShieldAlert } from "lucide-react";
import { PageShell } from "@/components/PageShell";
import { usePatient } from "@/hooks/queries/usePatients";
import { apiClient } from "@/lib/api-client";

interface FhirEntry {
  resource?: {
    resourceType?: string;
    id?: string;
    status?: string;
    title?: string;
    code?: { text?: string };
  };
}

interface FhirBundle {
  resourceType?: string;
  type?: string;
  timestamp?: string;
  entry?: FhirEntry[];
}

export default function IpsPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const { data: patientData, isLoading: patientLoading } = usePatient(patientId);
  const patient = patientData?.data;
  const cpid = patient?.attributes.cpid ? String(patient.attributes.cpid) : "";

  const bundleQuery = useQuery({
    queryKey: ["ips-summary", cpid],
    queryFn: async () => {
      const raw = await apiClient.getText(`/internal/v1/summary/ips/${encodeURIComponent(cpid)}`);
      return JSON.parse(raw) as FhirBundle;
    },
    enabled: Boolean(cpid),
  });

  const resourceCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const entry of bundleQuery.data?.entry ?? []) {
      const resourceType = entry.resource?.resourceType ?? "Unknown";
      counts.set(resourceType, (counts.get(resourceType) ?? 0) + 1);
    }

    return [...counts.entries()].sort((left, right) => right[1] - left[1]);
  }, [bundleQuery.data?.entry]);

  return (
    <PageShell title="International Patient Summary">
        <div className="space-y-6">
          <div className="rounded-2xl border border-indigo-200 bg-indigo-50/90 p-5">
            <div className="flex items-start gap-3">
              <Globe2 className="mt-0.5 h-5 w-5 shrink-0 text-indigo-700" />
              <div>
                <p className="text-sm font-semibold text-indigo-950">Butano summary through the Experience BFF</p>
                <p className="mt-1 text-sm text-indigo-900/90">
                  This chart view uses the patient&apos;s CPID and the canonical Experience path{" "}
                  <code className="rounded bg-white/80 px-1">/internal/v1/summary/ips/{"{cpid}"}</code>.
                </p>
                {cpid ? (
                  <p className="mt-2 text-xs text-indigo-800">
                    Patient CPID: <span className="font-mono">{cpid}</span>
                  </p>
                ) : (
                  <p className="mt-2 text-xs text-amber-800">
                    IPS is unavailable until this patient has a CPID linked in the chart context.
                  </p>
                )}
              </div>
            </div>
          </div>

          {patientLoading || bundleQuery.isLoading ? (
            <div className="flex items-center justify-center rounded-2xl border border-slate-200 bg-white p-12">
              <Loader2 className="h-6 w-6 animate-spin text-slate-400" />
            </div>
          ) : bundleQuery.isError ? (
            <div className="rounded-2xl border border-red-200 bg-red-50 p-6 text-sm text-red-900">
              <div className="flex items-start gap-3">
                <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-red-600" />
                <div>
                  <p className="font-semibold">Unable to load the IPS bundle</p>
                  <p className="mt-1">
                    {(bundleQuery.error as Error | null)?.message ??
                      "The Experience BFF could not return an IPS bundle for this patient."}
                  </p>
                </div>
              </div>
            </div>
          ) : !cpid ? (
            <div className="rounded-2xl border border-amber-200 bg-amber-50 p-6 text-sm text-amber-950">
              IPS cannot be generated here until the patient record exposes a CPID in Experience.
            </div>
          ) : (
            <>
              <div className="grid gap-4 md:grid-cols-3">
                <div className="rounded-2xl border border-slate-200 bg-white p-4">
                  <p className="text-xs uppercase tracking-[0.18em] text-slate-500">Bundle type</p>
                  <p className="mt-2 text-2xl font-semibold text-slate-900">{bundleQuery.data?.type ?? "DOCUMENT"}</p>
                </div>
                <div className="rounded-2xl border border-slate-200 bg-white p-4">
                  <p className="text-xs uppercase tracking-[0.18em] text-slate-500">Entries</p>
                  <p className="mt-2 text-2xl font-semibold text-slate-900">{bundleQuery.data?.entry?.length ?? 0}</p>
                </div>
                <div className="rounded-2xl border border-slate-200 bg-white p-4">
                  <p className="text-xs uppercase tracking-[0.18em] text-slate-500">Timestamp</p>
                  <p className="mt-2 text-sm font-semibold text-slate-900">
                    {bundleQuery.data?.timestamp
                      ? new Date(bundleQuery.data.timestamp).toLocaleString()
                      : "Not supplied"}
                  </p>
                </div>
              </div>

              <div className="rounded-2xl border border-slate-200 bg-white p-5">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <h2 className="text-base font-semibold text-slate-900">Bundle contents</h2>
                    <p className="mt-1 text-sm text-slate-600">
                      Resource counts help clinicians confirm what was included before exporting or handing over the summary.
                    </p>
                  </div>
                  <Link
                    href={`/ehr/${patientId}/timeline`}
                    className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
                  >
                    Timeline context
                  </Link>
                </div>
                <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                  {resourceCounts.map(([resourceType, count]) => (
                    <div key={resourceType} className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                      <p className="text-sm font-semibold text-slate-900">{resourceType}</p>
                      <p className="mt-1 text-xs text-slate-500">Resources in the IPS document</p>
                      <p className="mt-3 text-2xl font-semibold text-indigo-700">{count}</p>
                    </div>
                  ))}
                </div>
              </div>

              <div className="rounded-2xl border border-slate-200 bg-white p-5">
                <div className="flex items-center gap-2">
                  <FileJson className="h-4 w-4 text-slate-500" />
                  <h2 className="text-base font-semibold text-slate-900">Raw FHIR preview</h2>
                </div>
                <pre className="mt-4 max-h-[32rem] overflow-auto rounded-2xl bg-slate-950 p-4 text-xs text-slate-100">
                  {JSON.stringify(bundleQuery.data, null, 2)}
                </pre>
              </div>
            </>
          )}
        </div>
      </PageShell>
  );
}
