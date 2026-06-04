"use client";

import Link from "next/link";
import { useParams, usePathname } from "next/navigation";
import { X } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { useClinicalGuidance } from "@/components/clinical/ClinicalGuidanceContext";
import { ClinicalPathwaySessionPanel } from "@/components/clinical/ClinicalPathwaySessionPanel";
import { EdlizPrescribingPanel } from "@/components/clinical/EdlizPrescribingPanel";

function encounterIdFromPath(pathname: string): string | undefined {
  const m = pathname.match(/\/encounter\/([^/]+)/);
  return m?.[1];
}

export function ClinicalKnowledgeDock() {
  const { dock, closeDock, openDock } = useClinicalGuidance();
  const pathname = usePathname();
  const params = useParams<{ patientId?: string; encounterId?: string }>();
  const patientId = params.patientId;
  const encounterId = params.encounterId ?? encounterIdFromPath(pathname);
  const { user } = useAuthStore();
  const { isPrescriber } = useRoleGroup();
  const actorId = user?.id ?? "";

  const { data: conditionsData } = useQuery({
    queryKey: ["dock-cds-conditions", patientId],
    queryFn: () => apiClient.get<{ data: Array<{ attributes: Record<string, unknown> }> }>(
      `/internal/v1/conditions?patient_id=${patientId}`,
    ),
    enabled: Boolean(patientId) && dock.open && dock.tab === "prescribing",
  });

  const { data: medsData } = useQuery({
    queryKey: ["dock-cds-meds", patientId],
    queryFn: () =>
      apiClient.get<{ data: Array<{ attributes: Record<string, unknown> }> }>(
        `/internal/v1/pharmacy/prescriptions?patient_id=${patientId}`,
      ),
    enabled: Boolean(patientId) && dock.open && dock.tab === "prescribing",
  });

  if (!dock.open) return null;

  const diagnoses =
    (conditionsData?.data ?? []).map((c) => {
      const a = c.attributes as { condition_name?: string; icd_code?: string };
      return (a.condition_name || a.icd_code || "").trim();
    }) ?? [];

  const activeMedicationGenerics =
    (medsData?.data ?? []).map((m) => {
      const a = m.attributes as { generic_name?: string; medication_name?: string };
      const g = (a.generic_name || a.medication_name || "").toLowerCase().trim();
      return g.split(/\s+/)[0] ?? "";
    }) ?? [];

  return (
    <>
      <button
        type="button"
        aria-label="Close clinical guidance"
        className="fixed inset-0 z-[200] bg-black/20 lg:bg-transparent"
        onClick={closeDock}
      />
      <aside className="fixed inset-y-0 right-0 z-[210] flex w-full max-w-md flex-col border-l border-gray-200 bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-gray-100 px-3 py-2">
          <div className="flex gap-1 rounded-lg bg-gray-100 p-0.5 text-xs">
            <button
              type="button"
              onClick={() => openDock("pathways")}
              className={`rounded-md px-2 py-1 ${dock.tab === "pathways" ? "bg-white shadow-sm font-medium" : "text-gray-600"}`}
            >
              Pathways
            </button>
            <button
              type="button"
              onClick={() => openDock("prescribing")}
              className={`rounded-md px-2 py-1 ${dock.tab === "prescribing" ? "bg-white shadow-sm font-medium" : "text-gray-600"}`}
            >
              EDLIZ Rx
            </button>
          </div>
          <button
            type="button"
            onClick={closeDock}
            className="rounded p-1 text-gray-500 hover:bg-gray-100 hover:text-gray-800"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-3">
          {dock.tab === "pathways" ? (
            <ClinicalPathwaySessionPanel patientId={patientId} encounterId={encounterId} compact />
          ) : isPrescriber && patientId && encounterId && actorId ? (
            <EdlizPrescribingPanel
              patientId={patientId}
              encounterId={encounterId}
              actorId={actorId}
              diagnoses={diagnoses.filter(Boolean)}
              activeMedicationGenerics={activeMedicationGenerics.filter(Boolean)}
              showOrderDraftBridge
            />
          ) : (
            <div className="text-sm text-gray-600 space-y-2">
              {!isPrescriber ? (
                <p>Prescribing check is limited to prescriber roles.</p>
              ) : !patientId ? (
                <p>Open a patient chart to run the EDLIZ prescribing check.</p>
              ) : !encounterId ? (
                <p>
                  Open an{" "}
                  <Link href={`/ehr/${patientId}/encounters`} className="text-impilo-500 underline">
                    encounter
                  </Link>{" "}
                  to attach context to the prescribing evaluation.
                </p>
              ) : (
                <p>Sign in to use prescribing assist.</p>
              )}
            </div>
          )}
        </div>
        <div className="border-t border-gray-100 px-3 py-2 text-[10px] text-gray-500">
          <Link href="/ask" className="text-impilo-500 hover:underline" onClick={closeDock}>
            Open Ask (conversational EDLIZ)
          </Link>
        </div>
      </aside>
    </>
  );
}
