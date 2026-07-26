"use client";

import { useParams } from "next/navigation";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { ImamPanel } from "@/features/paediatrics/imam/ImamPanel";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useGrowth } from "@/hooks/queries/useGrowth";
import { usePatient } from "@/hooks/queries/usePatients";
import { useFacilityStore } from "@/hooks/useFacilityStore";

/**
 * Nutrition treatment for one child.
 *
 * The measurements from the child's latest growth contact are offered as the admission
 * anthropometry, because the commonest enrolment is the one that follows straight on from
 * weighing the child, and asking for the same arm circumference twice is how it gets mistyped.
 */
export default function ImamPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = typeof params?.patientId === "string" ? params.patientId : "";
  const facility = useFacilityStore((state) => state.facility);
  const { data: patientData } = usePatient(patientId);
  const { data: encountersData } = useEncounters(patientId);
  const { data: growthRows = [] } = useGrowth(patientId);

  const dateOfBirth = patientData?.data?.attributes?.dateOfBirth;
  const ageDays =
    typeof dateOfBirth === "string" && !Number.isNaN(new Date(dateOfBirth).getTime())
      ? Math.max(0, Math.floor((Date.now() - new Date(dateOfBirth).getTime()) / 86_400_000))
      : null;

  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE",
  );

  const latest = [...growthRows].sort(
    (a, b) => new Date(b.measuredAt).getTime() - new Date(a.measuredAt).getTime(),
  )[0];

  return (
    <EHRLayout>
      <PageShell
        title="Nutrition treatment"
        subtitle="Integrated management of acute malnutrition — enrolment, reviews, discharge criteria and defaulter tracing."
      >
        {patientId ? (
          <ImamPanel
            patientId={patientId}
            encounterId={activeEncounter?.id ?? null}
            facilityId={facility?.id ?? null}
            ageDays={ageDays}
            suggestedMuacCm={latest?.muacCm ?? null}
            suggestedWeightKg={latest?.weightKg ?? null}
          />
        ) : (
          <p className="text-sm text-muted-foreground">No patient selected.</p>
        )}
      </PageShell>
    </EHRLayout>
  );
}
