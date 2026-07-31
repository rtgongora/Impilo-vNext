"use client";

/**
 * Clinical History — narrative entry + §6 Clerking continuity compose.
 * Route: /ehr/[patientId]/history | pageTitle: "History"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import {
  ArrowRightLeft,
  Loader2,
  FileText,
  Activity,
  Stethoscope,
  Pill,
  ShieldAlert,
  Save,
  CheckCircle2,
} from "lucide-react";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { ClerkingContinuityShell } from "@/features/medicine/clerking/ClerkingContinuityShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient } from "@/lib/api-client";

export default function ClinicalHistoryPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const { user } = useAuthStore();
  const facility = useFacilityStore((state) => state.facility);

  const { data: encountersData, isLoading: loadingEnc } = useEncounters(patientId);
  const encounters = encountersData?.data ?? [];
  const activeEncounter = encounters.find((e) => e.attributes.isOpen);

  const [hpiText, setHpiText] = useState("");
  const [hpiSaving, setHpiSaving] = useState(false);
  const [hpiSaved, setHpiSaved] = useState(false);

  async function handleSaveHPI() {
    if (!activeEncounter || !hpiText.trim()) return;
    setHpiSaving(true);
    setHpiSaved(false);
    try {
      await apiClient.post("/internal/v1/clinical-notes", {
        patient_id: patientId,
        encounter_id: activeEncounter.id,
        note_type: "HISTORY",
        body: hpiText.trim(),
        author_id: user?.id ?? "system",
        author_name: user?.displayName ?? user?.email ?? "Provider",
      });
      setHpiSaved(true);
    } catch {
      // Error surfaced by leaving unsaved state
    } finally {
      setHpiSaving(false);
    }
  }

  return (
    <EHRLayout>
      <PageShell title="History">
        {loadingEnc ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <div className="space-y-5">
            <ClinicalReviewHeader
              badge="Longitudinal history"
              badgeIcon={FileText}
              title="Review the active story, then branch into structured continuity without inventing empty history"
              description="History is the narrative entry point: capture the present illness, then compose PMH, episodes, meds, allergies and related SoRs from the clerking continuity shell."
              facilityName={facility?.name}
              encounterLabel={
                activeEncounter
                  ? `${activeEncounter.attributes.encounterType} since ${new Date(activeEncounter.attributes.startedAt).toLocaleString()}`
                  : null
              }
              actions={[
                { href: `/ehr/${patientId}/summary`, label: "Summary", icon: Activity },
                { href: `/ehr/${patientId}/conditions`, label: "Conditions", icon: Stethoscope, tone: "secondary" },
                { href: `/ehr/${patientId}/medications`, label: "Medications", icon: Pill, tone: "secondary" },
                { href: `/ehr/${patientId}/allergies`, label: "Allergies", icon: ShieldAlert, tone: "secondary" },
                { href: `/ehr/${patientId}/notes`, label: "Notes", icon: ArrowRightLeft, tone: "secondary" },
              ]}
              metrics={[]}
            />

            {activeEncounter &&
              typeof (activeEncounter.attributes as Record<string, unknown>).chief_complaint ===
                "string" && (
                <div className="bg-card rounded-lg border border-border p-4">
                  <div className="flex items-center gap-2 mb-2">
                    <FileText className="w-4 h-4 text-impilo-400" />
                    <h3 className="text-sm font-medium text-foreground">Presenting Complaint</h3>
                  </div>
                  <p className="text-sm text-foreground">
                    {String((activeEncounter.attributes as Record<string, unknown>).chief_complaint)}
                  </p>
                </div>
              )}

            {activeEncounter && (
              <div className="bg-card rounded-lg border border-border p-4">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <FileText className="w-4 h-4 text-indigo-500" />
                    <h3 className="text-sm font-medium text-foreground">History of Present Illness</h3>
                  </div>
                  {hpiSaved && (
                    <span className="text-xs text-green-600 flex items-center gap-1">
                      <CheckCircle2 className="w-3 h-3" /> Saved
                    </span>
                  )}
                </div>
                <textarea
                  value={hpiText}
                  onChange={(e) => setHpiText(e.target.value)}
                  rows={4}
                  placeholder="Document the history of presenting illness — onset, duration, character, associated symptoms, aggravating/relieving factors, previous treatments..."
                  className="w-full px-3 py-2 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
                />
                <button
                  onClick={handleSaveHPI}
                  disabled={hpiSaving || !hpiText.trim()}
                  className="mt-2 px-4 py-1.5 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-primary disabled:opacity-50 flex items-center gap-2 transition-colors"
                >
                  {hpiSaving ? (
                    <>
                      <Loader2 className="w-3.5 h-3.5 animate-spin" /> Saving...
                    </>
                  ) : (
                    <>
                      <Save className="w-3.5 h-3.5" /> Save HPI
                    </>
                  )}
                </button>
              </div>
            )}

            <ClerkingContinuityShell patientId={patientId} />
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
