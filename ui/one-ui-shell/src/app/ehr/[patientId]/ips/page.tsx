"use client";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { EHR_CLINICAL_COLUMNS } from "@/lib/json-api/generic-table-columns";
import Link from "next/link";
import { useMemo } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { FileJson, Globe2, Loader2, ShieldAlert } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
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
    <EHRLayout>
      <PageShell title="International Patient Summary">
        <div className="space-y-6">
          <div className="rounded-2xl border border-info/25 bg-info-soft/90 p-5">
            <div className="flex items-start gap-3">
              <Globe2 className="mt-0.5 h-5 w-5 shrink-0 text-primary-hover" />
              <div>
                <p className="text-sm font-semibold text-primary-hover">Butano summary through the Experience BFF</p>
                <p className="mt-1 text-sm text-primary-hover/90">
                  This chart view uses the patient&apos;s CPID and the canonical Experience path{" "}
                  <code className="rounded bg-card/80 px-1">/internal/v1/summary/ips/{"{cpid}"}</code>.
                </p>
                {cpid ? (
                  <p className="mt-2 text-xs text-primary-hover">
                    Patient CPID: <span className="font-mono">{cpid}</span>
                  </p>
                ) : (
                  <p className="mt-2 text-xs text-warning-foreground">
                    IPS is unavailable until this patient has a CPID linked in the chart context.
                  </p>
                )}
              </div>
            </div>
          </div>

          {patientLoading || bundleQuery.isLoading ? (
            <div className="flex items-center justify-center rounded-2xl border border-border bg-card p-12">
              <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            </div>
          ) : bundleQuery.isError ? (
            <div className="rounded-2xl border border-danger/28 bg-danger-soft p-6 text-sm text-red-900">
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
            <div className="rounded-2xl border border-warning/35 bg-warning-soft p-6 text-sm text-warning-foreground">
              IPS cannot be generated here until the patient record exposes a CPID in Experience.
            </div>
          ) : (
            <>
              <div className="grid gap-4 md:grid-cols-3">
                <div className="rounded-2xl border border-border bg-card p-4">
                  <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Bundle type</p>
                  <p className="mt-2 text-2xl font-semibold text-foreground">{bundleQuery.data?.type ?? "DOCUMENT"}</p>
                </div>
                <div className="rounded-2xl border border-border bg-card p-4">
                  <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Entries</p>
                  <p className="mt-2 text-2xl font-semibold text-foreground">{bundleQuery.data?.entry?.length ?? 0}</p>
                </div>
                <div className="rounded-2xl border border-border bg-card p-4">
                  <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Timestamp</p>
                  <p className="mt-2 text-sm font-semibold text-foreground">
                    {bundleQuery.data?.timestamp
                      ? new Date(bundleQuery.data.timestamp).toLocaleString()
                      : "Not supplied"}
                  </p>
                </div>
              </div>

              <div className="rounded-2xl border border-border bg-card p-5">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <h2 className="text-base font-semibold text-foreground">Bundle contents</h2>
                    <p className="mt-1 text-sm text-muted-foreground">
                      Resource counts help clinicians confirm what was included before exporting or handing over the summary.
                    </p>
                  </div>
                  <Link
                    href={`/ehr/${patientId}/timeline`}
                    className="inline-flex items-center gap-2 rounded-xl border border-border bg-background px-4 py-2 text-sm font-medium text-foreground hover:bg-neutral-100"
                  >
                    Timeline context
                  </Link>
                </div>
                <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                  {resourceCounts.map(([resourceType, count]) => (
                    <div key={resourceType} className="rounded-2xl border border-border bg-background p-4">
                      <p className="text-sm font-semibold text-foreground">{resourceType}</p>
                      <p className="mt-1 text-xs text-muted-foreground">Resources in the IPS document</p>
                      <p className="mt-3 text-2xl font-semibold text-primary-hover">{count}</p>
                    </div>
                  ))}
                </div>
              </div>

              <div className="rounded-2xl border border-border bg-card p-5">
                <div className="flex items-center gap-2">
                  <FileJson className="h-4 w-4 text-muted-foreground" />
                  <h2 className="text-base font-semibold text-foreground">Raw FHIR preview</h2>
                </div>
                <JsonApiDataTable data={bundleQuery.data} columns={EHR_CLINICAL_COLUMNS} isLoading={bundleQuery.isPending} error={bundleQuery.error as Error | null} emptyTitle="No bundle entries" />
              </div>
            </>
          )}
        </div>
      </PageShell>
    </EHRLayout>
  );
}
