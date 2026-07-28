"use client";

/**
 * Medical ward round for one admitted patient.
 * Route: /ehr/[patientId]/ward-round | pageTitle: "Ward round"
 *
 * Patient-centred, complementing the ward-centred boards under /clinical/inpatient.
 */

import { useParams } from "next/navigation";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { MedicalWardRoundShell } from "@/features/medicine/ward/MedicalWardRoundShell";

export default function WardRoundPage() {
  const params = useParams();
  const patientId = typeof params?.patientId === "string" ? params.patientId : "";

  return (
    <EHRLayout>
      <PageShell
        title="Ward round"
        subtitle="Review this patient's problems and record the round entry against their admission."
      >
        {patientId ? (
          <MedicalWardRoundShell patientId={patientId} />
        ) : (
          <p className="text-sm text-muted-foreground">No patient selected.</p>
        )}
      </PageShell>
    </EHRLayout>
  );
}
