"use client";

/**
 * Facility Import Review queues — read-only review buckets for excluded/duplicate/acceptable-missing
 * rows. Honestly labelled: counts are real; per-row detail requires row-level staging (next slice).
 * Route: /admin/facility-imports/[runId]/review
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { Loader2, AlertTriangle, ArrowLeft, Info } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { useFacilityImportReview } from "@/hooks/queries/useFacilityImports";

const BUCKETS: Array<{ key: keyof ReviewBuckets; label: string; note: string }> = [
  {
    key: "missing_facility_code",
    label: "Excluded — missing facility code",
    note: "No facility code; never imported, no synthetic code generated.",
  },
  {
    key: "duplicate_facility_code",
    label: "Review — duplicate facility code",
    note: "Code already assigned; held for human resolution, no auto-merge.",
  },
  {
    key: "duplicate_facility_name",
    label: "Review — duplicate facility name",
    note: "Name duplicated in the batch; import only on explicit 'genuinely distinct' decision.",
  },
  {
    key: "acceptable_missing",
    label: "Imported — acceptable missing data",
    note: "Imported with visible gaps (coords/type/ownership/status); no fake defaults.",
  },
];

interface ReviewBuckets {
  missing_facility_code: number;
  duplicate_facility_code: number;
  duplicate_facility_name: number;
  acceptable_missing: number;
}

export default function FacilityImportReviewPage() {
  const params = useParams();
  const runId = params.runId as string;
  const { data, isLoading, isError } = useFacilityImportReview(runId);
  const review = data?.data;

  return (
    <AppLayout>
      <PageShell title="Facility Import Review" subtitle="Review queues for excluded and flagged rows">
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
              status="partial"
              detail="Real counts; per-row review detail pending row-level staging"
            />

            <div className="grid gap-3">
              {BUCKETS.map((b) => (
                <div
                  key={b.key}
                  className="bg-card rounded-lg border border-border p-4 flex items-center justify-between"
                >
                  <div>
                    <p className="font-medium text-foreground">{b.label}</p>
                    <p className="text-xs text-muted-foreground mt-0.5">{b.note}</p>
                  </div>
                  <span className="text-2xl font-bold text-foreground tabular-nums">
                    {review.buckets[b.key] ?? 0}
                  </span>
                </div>
              ))}
            </div>

            {!review.rowLevelDetailAvailable && (
              <div className="bg-primary-soft/40 rounded-lg border border-border p-4 flex gap-3">
                <Info className="w-5 h-5 text-primary shrink-0" />
                <p className="text-sm text-muted-foreground">{review.message}</p>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
