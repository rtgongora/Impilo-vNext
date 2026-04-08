"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Clock,
  Eye,
  FileText,
  Loader2,
  Shield,
  Upload,
  Users,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useFacilityStore } from "@/hooks/useFacilityStore";

interface Directive {
  id: string;
  type: "DNR" | "Living Will" | "Power of Attorney" | "Organ Donation" | "Mental Health Directive";
  status: "Active" | "Expired" | "Revoked" | "Pending Review";
  effectiveDate: string;
  reviewDate: string;
  documentRef: string | null;
  summary: string;
  contact: string | null;
  contactRelation: string | null;
  contactPhone: string | null;
}

const MOCK_DIRECTIVES: Directive[] = [
  {
    id: "ad-1", type: "DNR", status: "Active", effectiveDate: "2025-06-15", reviewDate: "2026-06-15",
    documentRef: "DOC-2025-0612", summary: "Full DNR in effect. Patient does not wish cardiopulmonary resuscitation or mechanical ventilation in the event of cardiac or respiratory arrest.",
    contact: "Mary M.", contactRelation: "Spouse", contactPhone: "+263 77 234 5678",
  },
  {
    id: "ad-2", type: "Living Will", status: "Active", effectiveDate: "2025-06-15", reviewDate: "2026-06-15",
    documentRef: "DOC-2025-0613", summary: "Patient wishes comfort care only in the event of terminal illness or persistent vegetative state. No artificial nutrition or hydration after 14 days. Pain management to be prioritized.",
    contact: null, contactRelation: null, contactPhone: null,
  },
  {
    id: "ad-3", type: "Power of Attorney", status: "Active", effectiveDate: "2025-06-15", reviewDate: "2026-06-15",
    documentRef: "DOC-2025-0614", summary: "Healthcare power of attorney granted to spouse Mary M. for medical decision-making when patient is incapacitated. Secondary: brother James M.",
    contact: "Mary M.", contactRelation: "Spouse", contactPhone: "+263 77 234 5678",
  },
  {
    id: "ad-4", type: "Organ Donation", status: "Active", effectiveDate: "2025-06-15", reviewDate: "2027-06-15",
    documentRef: null, summary: "Patient consents to organ donation upon death. All organs and tissues. Registered with National Organ Donation Registry (Reg# ZW-OD-2025-4421).",
    contact: null, contactRelation: null, contactPhone: null,
  },
  {
    id: "ad-5", type: "Mental Health Directive", status: "Pending Review", effectiveDate: "2024-12-01", reviewDate: "2025-12-01",
    documentRef: "DOC-2024-1188", summary: "Patient preferences for psychiatric treatment: prefers outpatient over inpatient. No electroconvulsive therapy. Preferred medications listed in attached document.",
    contact: "Dr. S. Chirwa", contactRelation: "Psychiatrist", contactPhone: "+263 24 276 1234",
  },
];

const STATUS_STYLES: Record<string, { bg: string; icon: React.ElementType }> = {
  Active: { bg: "bg-green-100 text-green-700", icon: CheckCircle2 },
  Expired: { bg: "bg-red-100 text-red-700", icon: AlertTriangle },
  Revoked: { bg: "bg-gray-100 text-gray-600", icon: AlertTriangle },
  "Pending Review": { bg: "bg-amber-100 text-amber-700", icon: Clock },
};

const TYPE_COLORS: Record<string, string> = {
  DNR: "bg-red-100 text-red-600",
  "Living Will": "bg-purple-100 text-purple-600",
  "Power of Attorney": "bg-blue-100 text-blue-600",
  "Organ Donation": "bg-green-100 text-green-600",
  "Mental Health Directive": "bg-teal-100 text-teal-600",
};

export default function AdvanceDirectivesPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const facility = useFacilityStore((state) => state.facility);
  const { data: encountersData } = useEncounters(patientId);

  const { data, isLoading } = useQuery({
    queryKey: ["advance-directives", patientId],
    queryFn: async () => ({ data: MOCK_DIRECTIVES }),
  });

  const directives = data?.data ?? [];
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE"
  );
  const dnr = directives.find((directive) => directive.type === "DNR" && directive.status === "Active");
  const needsReview = directives.filter((directive) => directive.status === "Pending Review" || directive.status === "Expired");
  const activeDirectives = directives.filter((directive) => directive.status === "Active").length;

  const [expandedId, setExpandedId] = useState<string | null>(null);

  return (
    <EHRLayout>
      <PageShell title="Advance Directives" subtitle="Patient advance care planning and legal directives">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading directives...</span>
          </div>
        ) : (
          <div className="space-y-6">
            <ClinicalReviewHeader
              badge="Advance directives"
              badgeIcon={Shield}
              title="Keep legal preferences and emergency limits visible wherever the care plan or encounter may escalate"
              description="Advance directives now sit in the same continuity layer as documents, care team, and notes so legal preferences stay visible when decisions become urgent."
              facilityName={facility?.name}
              encounterLabel={
                activeEncounter
                  ? `${activeEncounter.attributes.encounterType} since ${new Date(activeEncounter.attributes.startedAt).toLocaleString()}`
                  : null
              }
              actions={[
                { href: `/ehr/${patientId}/summary`, label: "Summary", icon: Activity },
                { href: `/ehr/${patientId}/documents`, label: "Documents", icon: FileText, tone: "secondary" },
                { href: `/ehr/${patientId}/care-team`, label: "Care Team", icon: Users, tone: "secondary" },
                { href: `/ehr/${patientId}/notes`, label: "Notes", icon: FileText, tone: "secondary" },
              ]}
              metrics={[
                {
                  label: "Active directives",
                  value: String(activeDirectives),
                  detail: "Preferences currently in force for this patient.",
                },
                {
                  label: "Review needed",
                  value: String(needsReview.length),
                  detail: "Directives that should be refreshed, verified, or re-documented.",
                },
                {
                  label: "DNR status",
                  value: dnr ? "Active" : "None",
                  detail: dnr ? "Active resuscitation limit is on file and must stay visible." : "No active DNR order is on file.",
                },
              ]}
            />

            <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Directive continuity</p>
              <p className="mt-2 text-sm text-slate-800">
                {dnr
                  ? "An active DNR is on file, so this surface needs to stay in the same workflow loop as notes, documents, and team communication whenever the encounter escalates."
                  : "No active DNR is documented, but directive review still needs to stay connected to documents and team communication so preferences are not missed later."}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Use Documents for source files, Care Team for accountable contacts, and Notes to confirm when preferences were reviewed or communicated.
              </p>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <FileText className="h-5 w-5 text-purple-600" />
                <h2 className="text-lg font-semibold text-gray-900">Advance Directives</h2>
              </div>
              <button className="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-blue-700">
                <Upload className="h-4 w-4" />
                Upload Directive
              </button>
            </div>

            {dnr && (
              <div className="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-4">
                <Shield className="mt-0.5 h-5 w-5 shrink-0 text-red-600" />
                <div>
                  <h3 className="text-sm font-semibold text-red-800">Active DNR Order</h3>
                  <p className="mt-1 text-xs text-red-700">{dnr.summary}</p>
                  <p className="mt-1 text-[10px] text-red-500">Effective since {dnr.effectiveDate} · Review by {dnr.reviewDate}</p>
                </div>
              </div>
            )}

            {needsReview.length > 0 && (
              <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
                <div className="mb-2 flex items-center gap-2">
                  <Clock className="h-4 w-4 text-amber-600" />
                  <h3 className="text-sm font-medium text-amber-800">{needsReview.length} directive{needsReview.length > 1 ? "s" : ""} requiring review</h3>
                </div>
                <div className="flex flex-wrap gap-2">
                  {needsReview.map((directive) => (
                    <span key={directive.id} className="rounded bg-amber-100 px-2 py-1 text-xs text-amber-800">{directive.type} - {directive.status}</span>
                  ))}
                </div>
              </div>
            )}

            {directives.length === 0 ? (
              <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
                <FileText className="mx-auto mb-3 h-10 w-10 text-gray-300" />
                <p className="text-sm text-gray-400">No advance directives on file</p>
              </div>
            ) : (
              <div className="space-y-3">
                {directives.map((directive) => {
                  const statusInfo = STATUS_STYLES[directive.status] ?? STATUS_STYLES.Active;
                  const StatusIcon = statusInfo.icon;
                  const isExpanded = expandedId === directive.id;

                  return (
                    <div key={directive.id} className="overflow-hidden rounded-lg border border-gray-200 bg-white">
                      <button
                        type="button"
                        onClick={() => setExpandedId(isExpanded ? null : directive.id)}
                        className="flex w-full items-center justify-between px-5 py-4 text-left transition-colors hover:bg-gray-50"
                      >
                        <div className="flex items-center gap-3">
                          <div className={`rounded px-2.5 py-1 text-xs font-medium ${TYPE_COLORS[directive.type]}`}>
                            {directive.type}
                          </div>
                          <div>
                            <p className="text-sm text-gray-500">Effective: {directive.effectiveDate}</p>
                          </div>
                        </div>
                        <div className="flex items-center gap-2">
                          <StatusIcon className={`h-4 w-4 ${statusInfo.bg.includes("green") ? "text-green-600" : statusInfo.bg.includes("amber") ? "text-amber-600" : "text-red-600"}`} />
                          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusInfo.bg}`}>{directive.status}</span>
                        </div>
                      </button>

                      {isExpanded && (
                        <div className="space-y-3 border-t border-gray-200 px-5 py-4">
                          <p className="text-sm text-gray-700">{directive.summary}</p>
                          <div className="grid grid-cols-2 gap-4 text-xs md:grid-cols-4">
                            <div>
                              <p className="text-gray-500">Effective Date</p>
                              <p className="font-medium text-gray-900">{directive.effectiveDate}</p>
                            </div>
                            <div>
                              <p className="text-gray-500">Review Date</p>
                              <p className="font-medium text-gray-900">{directive.reviewDate}</p>
                            </div>
                            <div>
                              <p className="text-gray-500">Document Ref</p>
                              <p className="font-medium text-gray-900">{directive.documentRef || "Not uploaded"}</p>
                            </div>
                            {directive.contact && (
                              <div>
                                <p className="text-gray-500">Contact</p>
                                <p className="font-medium text-gray-900">{directive.contact} ({directive.contactRelation})</p>
                                <p className="text-gray-500">{directive.contactPhone}</p>
                              </div>
                            )}
                          </div>
                          <div className="flex items-center gap-2 pt-2">
                            {directive.documentRef && (
                              <button className="inline-flex items-center gap-1 rounded-lg border border-blue-200 px-3 py-1.5 text-xs font-medium text-blue-600 transition-colors hover:bg-blue-50">
                                <Eye className="h-3 w-3" /> View Document
                              </button>
                            )}
                            <button className="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-600 transition-colors hover:bg-gray-50">
                              Edit
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
