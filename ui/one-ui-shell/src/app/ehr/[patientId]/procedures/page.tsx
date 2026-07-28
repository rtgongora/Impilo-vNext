"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { Calendar, ClipboardList, FileText, Filter, Loader2, Plus, Scissors } from "lucide-react";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import type { PatientProcedure } from "@/hooks/queries/useStructuredHistory";
import { usePatientProcedures } from "@/hooks/queries/useStructuredHistory";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useCreateProcedureEpisode } from "@/hooks/queries/useProcedureEpisode";

const STATUS_STYLES: Record<string, string> = {
  Completed: "bg-green-100 text-green-700",
  Scheduled: "bg-primary-soft text-primary",
  Cancelled: "bg-red-100 text-danger",
  "In Progress": "bg-amber-100 text-warning-foreground",
};

const PROCEDURE_TYPES = ["All", "General Surgery", "Endoscopy", "Orthopaedic", "Ophthalmology", "Radiology", "ENT"];

export default function ProceduresPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const router = useRouter();
  const facility = useFacilityStore((state) => state.facility);
  const { data: encountersData } = useEncounters(patientId);
  const createEpisode = useCreateProcedureEpisode(patientId);

  const { data, isLoading, isError, refetch } = usePatientProcedures(patientId);
  const procedures: PatientProcedure[] = data?.data ?? [];
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.isOpen
  );
  const scheduledCount = procedures.filter((procedure) => procedure.status === "Scheduled").length;
  const completedCount = procedures.filter((procedure) => procedure.status === "Completed").length;
  const crossFacilityCount = procedures.filter((procedure) => procedure.facility !== facility?.name).length;

  const [typeFilter, setTypeFilter] = useState("All");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [showFilters, setShowFilters] = useState(false);

  const filtered = procedures.filter((procedure) => {
    if (typeFilter !== "All" && procedure.type !== typeFilter) return false;
    if (dateFrom && procedure.date < dateFrom) return false;
    if (dateTo && procedure.date > dateTo) return false;
    return true;
  });

  return (
    <EHRLayout>
      <PageShell title="Procedures" subtitle="Past and scheduled procedure history">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading procedures...</span>
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center justify-center gap-3 py-16">
            <p className="text-sm text-muted-foreground">Unable to load procedures for this patient.</p>
            <button
              type="button"
              onClick={() => void refetch()}
              className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover"
            >
              Retry
            </button>
          </div>
        ) : (
          <div className="space-y-6">
            <ClinicalReviewHeader
              badge="Procedure continuity"
              badgeIcon={Scissors}
              title="Keep scheduled and completed procedures connected to the wider care loop instead of leaving them as isolated history entries"
              description="Procedure history now sits closer to documents, plans, and notes so upcoming interventions and completed outcomes are easier to carry across the patient workflow."
              facilityName={facility?.name}
              encounterLabel={
                activeEncounter
                  ? `${activeEncounter.attributes.encounterType} since ${new Date(activeEncounter.attributes.startedAt).toLocaleString()}`
                  : null
              }
              actions={[
                { href: `/ehr/${patientId}/summary`, label: "Summary", icon: ClipboardList },
                { href: `/ehr/${patientId}/documents`, label: "Documents", icon: FileText, tone: "secondary" },
                { href: `/ehr/${patientId}/care-plans`, label: "Care Plans", icon: ClipboardList, tone: "secondary" },
                { href: `/ehr/${patientId}/notes`, label: "Notes", icon: FileText, tone: "secondary" },
              ]}
              metrics={[
                {
                  label: "Scheduled",
                  value: String(scheduledCount),
                  detail: "Upcoming procedures that still need preparation and communication.",
                },
                {
                  label: "Completed",
                  value: String(completedCount),
                  detail: "Historic interventions already part of the record.",
                },
                {
                  label: "Cross-facility",
                  value: String(crossFacilityCount),
                  detail: crossFacilityCount > 0 ? "Some procedures occurred outside the current facility." : "All procedure history is within the current facility.",
                },
              ]}
            />

            <div className="rounded-3xl border border-border bg-background/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">Procedure continuity</p>
              <p className="mt-2 text-sm text-foreground">
                {scheduledCount > 0
                  ? `${scheduledCount} procedure${scheduledCount === 1 ? " is" : "s are"} still scheduled, so this workspace should stay linked to documents, notes, and plans until preparation and follow-up are complete.`
                  : "No pending procedures are visible; the continuity need is keeping the outcome and facility history accessible when plans or notes reference prior interventions."}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                Use Documents for source reports, Care Plans for follow-through tasks, and Notes for peri-procedural communication or outcome documentation.
              </p>
            </div>

            <div className="flex items-center justify-between flex-wrap gap-3">
              <div className="flex items-center gap-2">
                <Scissors className="h-5 w-5 text-purple-600" />
                <h2 className="text-lg font-semibold text-foreground">Procedure History</h2>
                <span className="text-sm text-muted-foreground">({filtered.length})</span>
              </div>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  disabled={createEpisode.isPending}
                  onClick={() => createEpisode.mutate({
                    procedureName: "Scheduled procedure",
                    encounterId: activeEncounter?.id,
                    scheduledAt: new Date().toISOString(),
                  }, {
                    onSuccess: (data) => {
                      if (data?.id) router.push(`/ehr/${patientId}/procedures/${data.id}`);
                    },
                  })}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-white hover:bg-primary-hover"
                >
                  <Plus className="h-4 w-4" />
                  New procedure case
                </button>
                <button
                  type="button"
                  onClick={() => setShowFilters((prev) => !prev)}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-2 text-sm font-medium text-foreground transition-colors hover:bg-background"
                >
                  <Filter className="h-4 w-4" />
                  Filters
                </button>
              </div>
            </div>

            {showFilters && (
              <div className="rounded-lg border border-border bg-card p-4">
                <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">Procedure Type</label>
                    <select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)} className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40">
                      {PROCEDURE_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">From Date</label>
                    <input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40" />
                  </div>
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">To Date</label>
                    <input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40" />
                  </div>
                </div>
              </div>
            )}

            {filtered.length === 0 ? (
              <div className="rounded-lg border border-border bg-card p-12 text-center">
                <Scissors className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
                <p className="text-sm text-muted-foreground">No procedures found</p>
              </div>
            ) : (
              <div className="overflow-hidden rounded-lg border border-border bg-card">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-border bg-background">
                        <th className="px-4 py-3 text-left font-medium text-muted-foreground">Date</th>
                        <th className="px-4 py-3 text-left font-medium text-muted-foreground">Procedure</th>
                        <th className="px-4 py-3 text-left font-medium text-muted-foreground">Type</th>
                        <th className="px-4 py-3 text-left font-medium text-muted-foreground">Surgeon</th>
                        <th className="px-4 py-3 text-left font-medium text-muted-foreground">Facility</th>
                        <th className="px-4 py-3 text-left font-medium text-muted-foreground">Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filtered.map((procedure) => (
                        <tr key={procedure.id} className="border-b border-border transition-colors hover:bg-background">
                          <td className="flex items-center gap-1.5 px-4 py-3 text-foreground">
                            <Calendar className="h-3.5 w-3.5 text-muted-foreground" />
                            {procedure.date}
                          </td>
                          <td className="px-4 py-3">
                            <Link href={`/ehr/${patientId}/procedures/${procedure.id}`} className="font-medium text-primary hover:underline">
                              {procedure.name}
                            </Link>
                            <div className="text-xs text-muted-foreground">{procedure.notes}</div>
                          </td>
                          <td className="px-4 py-3 text-foreground">{procedure.type}</td>
                          <td className="px-4 py-3 text-foreground">{procedure.surgeon}</td>
                          <td className="px-4 py-3 text-foreground">{procedure.facility}</td>
                          <td className="px-4 py-3">
                            <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLES[procedure.status] ?? "bg-neutral-100 text-muted-foreground"}`}>{procedure.status}</span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
