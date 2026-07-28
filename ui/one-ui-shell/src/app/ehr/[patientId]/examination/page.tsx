"use client";

/**
 * Structured physical examination for one patient (brief.md §7).
 * Route: /ehr/[patientId]/examination | pageTitle: "Examination"
 *
 * Thin by design: the shell lives in features/medicine/examination so it can be tested without
 * mounting the chart.
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { ExaminationShell } from "@/features/medicine/examination/ExaminationShell";

export default function ExaminationPage() {
  const params = useParams();
  const patientId = typeof params?.patientId === "string" ? params.patientId : "";

  return (
    <EHRLayout>
      <PageShell
        title="Examination"
        subtitle="Twenty-two regions, six states. Not examined is not a finding of normality."
      >
        <div className="space-y-4">
          {patientId ? (
            <ExaminationShell patientId={patientId} />
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
