"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { Baby, HeartPulse } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { VitalsCtgSection } from "@/features/maternity/ctg/VitalsCtgSection";
import { VitalsPartographSection } from "@/features/maternity/partograph/VitalsPartographSection";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { useEncounters } from "@/hooks/queries/useEncounters";

export default function MaternityMonitoringPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const { user } = useAuthStore();
  const { isClinical } = useRoleGroup();
  const { data: encountersData } = useEncounters(patientId);
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) => encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE",
  );
  const encounterId = activeEncounter?.id ?? "";

  return (
    <EHRLayout>
      <PageShell
        title="Maternity Monitoring"
        subtitle="Canonical maternity surface for partograph and CTG monitoring. Legacy labour rows stay on Vitals as compatibility-only."
      >
        <div className="mb-4 flex flex-wrap items-center gap-3 text-sm text-gray-500">
          <Link href={`/ehr/${encodeURIComponent(patientId)}/vitals`} className="inline-flex items-center gap-1 hover:text-gray-700">
            <HeartPulse className="h-4 w-4" /> Back to vitals
          </Link>
          <span className="inline-flex items-center gap-1">
            <Baby className="h-4 w-4 text-pink-600" />
            {activeEncounter ? `Active encounter: ${encounterId}` : "No active encounter in scope"}
          </span>
        </div>

        <section className="rounded-lg border border-pink-200/90 bg-white p-5">
          <h2 className="text-lg font-semibold text-gray-900">Partograph & CTG</h2>
          <p className="mt-1 text-xs text-gray-500">
            This route reuses the same maternity feature modules mounted on Vitals, so promotion to a first-class
            maternity workflow keeps the BFF contract and UI behavior aligned.
          </p>

          <VitalsPartographSection
            patientId={patientId}
            encounterId={encounterId}
            hasActiveEncounter={Boolean(activeEncounter && encounterId)}
            isClinical={isClinical}
            recordedBy={user?.id ?? user?.displayName ?? "unknown"}
          />

          <VitalsCtgSection
            patientId={patientId}
            encounterId={encounterId}
            hasActiveEncounter={Boolean(activeEncounter && encounterId)}
            isClinical={isClinical}
            recordedBy={user?.id ?? user?.displayName ?? "unknown"}
          />
        </section>
      </PageShell>
    </EHRLayout>
  );
}
