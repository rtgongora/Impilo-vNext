"use client";

/**
 * The multimorbidity view for one patient (brief.md §9).
 * Route: /ehr/[patientId]/multimorbidity | pageTitle: "Multimorbidity"
 *
 * Thin by design: the shell lives in features/medicine/multimorbidity so it can be tested without
 * mounting the chart.
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { MultimorbidityShell } from "@/features/medicine/multimorbidity/MultimorbidityShell";

export default function MultimorbidityPage() {
  const params = useParams();
  const patientId = typeof params?.patientId === "string" ? params.patientId : "";

  return (
    <EHRLayout>
      <PageShell
        title="Multimorbidity"
        subtitle="The whole patient at once — conflicts, burden and duplication that no single-disease view can see."
      >
        <div className="space-y-4">
          <div className="rounded-lg border border-border bg-muted/30 p-4 text-sm text-muted-foreground">
            Each check reports one of three answers: something was found, nothing was found, or the
            source could not be read. The third is <strong>not</strong> reassurance — it means the
            question was never asked.
          </div>

          {patientId ? (
            <MultimorbidityShell patientId={patientId} />
          ) : (
            <p className="text-sm text-muted-foreground">No patient selected.</p>
          )}

          <Link
            href={`/ehr/${patientId}/medicine`}
            className="inline-block text-sm text-primary hover:underline"
          >
            ← Back to the medicine workspace
          </Link>
        </div>
      </PageShell>
    </EHRLayout>
  );
}
