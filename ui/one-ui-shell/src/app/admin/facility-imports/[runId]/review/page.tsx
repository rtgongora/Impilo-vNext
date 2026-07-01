"use client";

/**
 * Facility Import Review queues — real review buckets backed by persisted row-level staging.
 * Counts and row previews come from the row-level API; full row detail is on the batch detail page.
 * Route: /admin/facility-imports/[runId]/review
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { Loader2, AlertTriangle, ArrowLeft } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import {
  useFacilityImportReview,
  type FacilityImportReviewBuckets,
  type ImportBucket,
} from "@/hooks/queries/useFacilityImports";

const BUCKETS: Array<{ key: keyof Omit<FacilityImportReviewBuckets, "runId">; label: string; note: string }> = [
  { key: "importedRows", label: "Imported rows", note: "Persisted as facilities (pending configuration)." },
  {
    key: "missingFacilityCode",
    label: "Excluded — missing facility code",
    note: "No facility code; never imported, no synthetic code generated.",
  },
  {
    key: "duplicateFacilityCodes",
    label: "Review — duplicate facility code",
    note: "Code already assigned; held for human resolution, no auto-merge.",
  },
  {
    key: "duplicateFacilityNames",
    label: "Review — duplicate facility name",
    note: "Name duplicated in the batch; import only on explicit 'genuinely distinct' decision.",
  },
  {
    key: "acceptableMissingFields",
    label: "Imported — acceptable missing data",
    note: "Imported with visible gaps (coords/type/ownership/status); no fake defaults.",
  },
  { key: "failedRows", label: "Failed rows", note: "Rows that errored during import." },
];

export default function FacilityImportReviewPage() {
  const params = useParams();
  const runId = params.runId as string;
  const { data, isLoading, isError } = useFacilityImportReview(runId);
  const review = data?.data;

  return (
    <AppLayout>
      <PageShell title="Facility Import Review" subtitle="Review queues backed by persisted row staging">
        <div className="mb-4">
          <Link
            href={`/admin/facility-imports/${runId}`}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Batch
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : isError || !review ? (
          <div className="bg-danger-soft rounded-lg border border-danger/28 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load review queues</p>
          </div>
        ) : (
          <div className="max-w-3xl space-y-4">
            <FeatureMaturityBadge
              status="connected"
              detail="Real counts + row previews from persisted row-level staging"
            />

            <div className="grid gap-3">
              {BUCKETS.map((b) => {
                const bucket = review[b.key] as ImportBucket;
                const preview = bucket?.preview ?? [];
                return (
                  <div key={b.key} className="bg-card rounded-lg border border-border p-4">
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="font-medium text-foreground">{b.label}</p>
                        <p className="text-xs text-muted-foreground mt-0.5">{b.note}</p>
                      </div>
                      <span className="text-2xl font-bold text-foreground tabular-nums">
                        {bucket?.count ?? 0}
                      </span>
                    </div>
                    {preview.length > 0 && (
                      <ul className="mt-2 text-xs text-muted-foreground space-y-0.5">
                        {preview.map((r) => (
                          <li key={r.id}>
                            {r.facilityName || "(no name)"}
                            {r.facilityCode ? ` · ${r.facilityCode}` : ""}
                            {r.district ? ` · ${r.district}` : ""}
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                );
              })}
            </div>

            <Link
              href={`/admin/facility-imports/${runId}`}
              className="inline-block text-sm text-primary hover:text-primary-hover"
            >
              View full row detail on the batch page →
            </Link>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
