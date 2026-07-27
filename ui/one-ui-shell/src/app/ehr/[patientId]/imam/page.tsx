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
  // These three reads only enrich the enrolment form — the child's age, the active encounter to
  // bind the write to, and the latest measurements to pre-fill. The actual clinical content
  // (episodes, reviews, the worklist) is fetched inside ImamPanel, which fails loudly on its own.
  // But a discarded error here is still a false claim: a failed patient read would silently read
  // as "no date of birth", a failed encounter read as "not in an active encounter", a failed
  // growth read as "no prior measurement" — each a fact the clinician might act on. So the errors
  // are captured and surfaced as "could not be loaded", never passed down as an absence.
  const { data: patientData, isError: patientError } = usePatient(patientId);
  const { data: encountersData, isError: encountersError } = useEncounters(patientId);
  const { data: growthRows = [], isError: growthError } = useGrowth(patientId);

  const enrichmentUnavailable = [
    patientError ? "the child's age" : null,
    encountersError ? "the active encounter" : null,
    growthError ? "the latest measurements" : null,
  ].filter((label): label is string => label !== null);

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
        {patientId && enrichmentUnavailable.length > 0 && (
          <div
            className="mb-4 rounded-lg border border-amber-500 bg-amber-50 p-3"
            data-testid="imam-enrichment-unavailable"
          >
            <p className="text-sm font-semibold text-amber-900">
              Some context could not be loaded
            </p>
            <p className="mt-1 text-xs text-amber-900">
              {enrichmentUnavailable.join(", ")} could not be read, so the enrolment form is not
              pre-filled from them. Enter the values directly — a blank field here is a failed read,
              not an absence of data about this child.
            </p>
          </div>
        )}
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
