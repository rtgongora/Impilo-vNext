"use client";

import { useState } from "react";
import { AlertTriangle, Loader2, ShieldAlert } from "lucide-react";
import { PlaneWorkspaceShell } from "@/components/workspace/PlaneWorkspaceShell";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";
import { WorkspaceEmptyState } from "@/components/workspace/WorkspaceEmptyState";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import {
  useEscalations,
  useAcknowledgeEscalation,
  useRespondEscalation,
  type InpatientEscalation,
} from "@/hooks/queries/useInpatient";

const INPATIENT_TABS = [
  { label: "Overview", href: "/clinical/inpatient" },
  { label: "Admissions", href: "/clinical/inpatient/admissions" },
  { label: "Ward board", href: "/clinical/inpatient/ward-board" },
  { label: "Nursing", href: "/clinical/inpatient/nursing" },
  { label: "Escalations", href: "/clinical/inpatient/escalations" },
  { label: "Discharge", href: "/clinical/inpatient/discharge-board" },
];

function rows(payload: unknown): InpatientEscalation[] {
  if (!payload || typeof payload !== "object") return [];
  const data = (payload as Record<string, unknown>).data;
  return Array.isArray(data) ? (data as InpatientEscalation[]) : [];
}

function riskClass(level?: string): string {
  switch (level) {
    case "HIGH":
      return "bg-red-50 text-red-700 border-red-200";
    case "MEDIUM":
      return "bg-amber-50 text-amber-700 border-amber-200";
    default:
      return "bg-slate-50 text-slate-600 border-slate-200";
  }
}

export default function InpatientEscalationsPage() {
  const [wardId, setWardId] = useState<string>("");
  const { data, isLoading, isError } = useEscalations(wardId ? { wardId } : {});
  const acknowledge = useAcknowledgeEscalation();
  const respond = useRespondEscalation();

  const escalations = rows(data);

  return (
    <PlaneWorkspaceShell
      title="Deterioration escalations"
      subtitle="Early-warning escalations awaiting clinician response"
      plane="Clinical Plane"
      tabs={INPATIENT_TABS}
    >
      <TrustContextBanner purposeOfUse="INPATIENT_CARE" />
      <NompiloContextualGuidance routePath="/clinical/inpatient/escalations" />

      <div className="mb-4 flex items-center gap-2">
        <label className="text-sm text-slate-600" htmlFor="ward-filter">
          Ward
        </label>
        <input
          id="ward-filter"
          value={wardId}
          onChange={(e) => setWardId(e.target.value)}
          placeholder="Ward id"
          className="rounded border border-slate-300 px-2 py-1 text-sm"
        />
      </div>

      {isLoading && (
        <div className="flex items-center gap-2 text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading escalations…
        </div>
      )}

      {isError && (
        <WorkspaceEmptyState
          icon={<ShieldAlert className="h-6 w-6" />}
          title="Escalations unavailable"
          description="The inpatient escalation service could not be reached. No data is shown rather than placeholder values."
        />
      )}

      {!isLoading && !isError && !wardId && (
        <WorkspaceEmptyState
          icon={<AlertTriangle className="h-6 w-6" />}
          title="Enter a ward to see open escalations"
          description="Deterioration escalations are scoped to a ward. Enter a ward id above to load the open list."
        />
      )}

      {!isLoading && !isError && wardId && escalations.length === 0 && (
        <WorkspaceEmptyState
          icon={<AlertTriangle className="h-6 w-6" />}
          title="No open escalations"
          description="No patients on this ward currently have an open deterioration escalation."
        />
      )}

      {escalations.length > 0 && (
        <ul className="space-y-3">
          {escalations.map((esc) => {
            const id = esc.escalation_id ?? esc.id ?? "";
            return (
              <li
                key={id}
                className={`rounded-lg border p-4 ${riskClass(esc.risk_level)}`}
              >
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <div className="font-semibold">
                      EWS {esc.total_score} · {esc.risk_level}
                    </div>
                    <div className="text-sm opacity-80">
                      Patient {esc.subject_cpid} · status {esc.status}
                    </div>
                    {esc.response_due_at && (
                      <div className="text-xs opacity-70">
                        Response due {new Date(esc.response_due_at).toLocaleString()}
                      </div>
                    )}
                  </div>
                  <div className="flex shrink-0 gap-2">
                    <button
                      type="button"
                      disabled={acknowledge.isPending}
                      onClick={() => acknowledge.mutate({ escalationId: id })}
                      className="rounded border border-current px-3 py-1 text-sm hover:bg-white/40 disabled:opacity-50"
                    >
                      Acknowledge
                    </button>
                    <button
                      type="button"
                      disabled={respond.isPending}
                      onClick={() =>
                        respond.mutate({ escalationId: id, note: "Reviewed at bedside" })
                      }
                      className="rounded bg-slate-900 px-3 py-1 text-sm text-white hover:bg-slate-700 disabled:opacity-50"
                    >
                      Respond
                    </button>
                  </div>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </PlaneWorkspaceShell>
  );
}
