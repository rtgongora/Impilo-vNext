"use client";

import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { ImamTracingQueue } from "@/features/paediatrics/imam/ImamTracingQueue";
import { useFacilityStore } from "@/hooks/useFacilityStore";

/**
 * The facility's defaulter tracing worklist.
 *
 * A separate screen from the child's record because it is a different job, done by different
 * people — a community health worker planning home visits, not the nurse who saw the child last.
 */
export default function NutritionTracingPage() {
  const facility = useFacilityStore((state) => state.facility);

  return (
    <EHRLayout>
      <PageShell
        title="Nutrition defaulter tracing"
        subtitle="Children in a nutrition programme whose review has come and gone, most overdue first."
      >
        {facility ? (
          <>
            <p className="mb-4 text-xs text-muted-foreground">
              {facility.name} · a child is traced after one missed review, and meets the defaulter
              definition after two.
            </p>
            <ImamTracingQueue facilityId={facility.id} />
          </>
        ) : (
          <div className="rounded-lg border border-amber-500 bg-amber-50 p-4">
            <p className="text-sm font-semibold text-amber-900">No facility is selected</p>
            <p className="mt-1 text-xs text-amber-900">
              The tracing worklist is per facility. Choose a facility to see which children are
              overdue — an unfiltered list would mix in children another clinic is following up.
            </p>
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
