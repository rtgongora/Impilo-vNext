"use client";

/**
 * Adult medicine workspace — the chronic and programme-based view of one patient.
 * Route: /ehr/[patientId]/medicine | pageTitle: "Medicine workspace"
 *
 * Thin by design: the logic lives in features/medicine/workspace so it can be tested without
 * mounting the chart. Mirrors the paediatric workspace page.
 */

import { useParams } from "next/navigation";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { MedicineWorkspaceShell } from "@/features/medicine/workspace/MedicineWorkspaceShell";

export default function MedicineWorkspacePage() {
  const params = useParams();
  const patientId = typeof params?.patientId === "string" ? params.patientId : "";

  return (
    <EHRLayout>
      <PageShell
        title="Medicine workspace"
        subtitle="Care programmes, the problem list and allergies for this patient, in one view."
      >
        {patientId ? (
          <MedicineWorkspaceShell patientId={patientId} />
        ) : (
          <p className="text-sm text-muted-foreground">No patient selected.</p>
        )}
      </PageShell>
    </EHRLayout>
  );
}
