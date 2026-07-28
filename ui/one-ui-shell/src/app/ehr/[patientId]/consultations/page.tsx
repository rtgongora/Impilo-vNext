"use client";

/**
 * Consultation and multidisciplinary decisions for one patient (brief.md §14).
 * Route: /ehr/[patientId]/consultations | pageTitle: "Consultations and MDT"
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { ConsultationShell } from "@/features/medicine/consultation/ConsultationShell";

export default function ConsultationsPage() {
  const params = useParams();
  const patientId = typeof params?.patientId === "string" ? params.patientId : "";

  return (
    <EHRLayout>
      <PageShell
        title="Consultations and MDT"
        subtitle="Asking another team a question never hands them the patient."
      >
        <div className="space-y-4">
          {patientId ? (
            <ConsultationShell patientId={patientId} />
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
