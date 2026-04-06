"use client";

/**
 * Advance Directives — DNR status, living will, power of attorney, organ donation.
 * Route: /ehr/[patientId]/advance-directives | pageTitle: "Advance Directives"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import { FileText, Loader2, Shield, AlertTriangle, CheckCircle2, Clock, Upload, Eye } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

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
  "Active": { bg: "bg-green-100 text-green-700", icon: CheckCircle2 },
  "Expired": { bg: "bg-red-100 text-red-700", icon: AlertTriangle },
  "Revoked": { bg: "bg-gray-100 text-gray-600", icon: AlertTriangle },
  "Pending Review": { bg: "bg-amber-100 text-amber-700", icon: Clock },
};

const TYPE_COLORS: Record<string, string> = {
  "DNR": "bg-red-100 text-red-600",
  "Living Will": "bg-purple-100 text-purple-600",
  "Power of Attorney": "bg-blue-100 text-blue-600",
  "Organ Donation": "bg-green-100 text-green-600",
  "Mental Health Directive": "bg-teal-100 text-teal-600",
};

export default function AdvanceDirectivesPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { data, isLoading } = useQuery({
    queryKey: ["advance-directives", patientId],
    queryFn: async () => ({ data: MOCK_DIRECTIVES }),
  });

  const directives = data?.data ?? [];
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const dnr = directives.find((d) => d.type === "DNR" && d.status === "Active");
  const needsReview = directives.filter((d) => d.status === "Pending Review" || d.status === "Expired");

  return (
    <EHRLayout>
      <PageShell title="Advance Directives" subtitle="Patient advance care planning and legal directives">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading directives...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <FileText className="w-5 h-5 text-purple-600" />
                <h2 className="text-lg font-semibold text-gray-900">Advance Directives</h2>
              </div>
              <button className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors">
                <Upload className="w-4 h-4" />
                Upload Directive
              </button>
            </div>

            {/* DNR Alert Banner */}
            {dnr && (
              <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-start gap-3">
                <Shield className="w-5 h-5 text-red-600 mt-0.5 shrink-0" />
                <div>
                  <h3 className="text-sm font-semibold text-red-800">Active DNR Order</h3>
                  <p className="text-xs text-red-700 mt-1">{dnr.summary}</p>
                  <p className="text-[10px] text-red-500 mt-1">Effective since {dnr.effectiveDate} &middot; Review by {dnr.reviewDate}</p>
                </div>
              </div>
            )}

            {/* Review Needed */}
            {needsReview.length > 0 && (
              <div className="bg-amber-50 border border-amber-200 rounded-lg p-4">
                <div className="flex items-center gap-2 mb-2">
                  <Clock className="w-4 h-4 text-amber-600" />
                  <h3 className="text-sm font-medium text-amber-800">{needsReview.length} directive{needsReview.length > 1 ? "s" : ""} requiring review</h3>
                </div>
                <div className="flex flex-wrap gap-2">
                  {needsReview.map((d) => (
                    <span key={d.id} className="px-2 py-1 bg-amber-100 text-amber-800 rounded text-xs">{d.type} — {d.status}</span>
                  ))}
                </div>
              </div>
            )}

            {/* Directive Cards */}
            {directives.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <FileText className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No advance directives on file</p>
              </div>
            ) : (
              <div className="space-y-3">
                {directives.map((directive) => {
                  const statusInfo = STATUS_STYLES[directive.status] || STATUS_STYLES["Active"];
                  const StatusIcon = statusInfo.icon;
                  const isExpanded = expandedId === directive.id;

                  return (
                    <div key={directive.id} className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                      <button
                        type="button"
                        onClick={() => setExpandedId(isExpanded ? null : directive.id)}
                        className="w-full px-5 py-4 flex items-center justify-between hover:bg-gray-50 transition-colors text-left"
                      >
                        <div className="flex items-center gap-3">
                          <div className={`px-2.5 py-1 rounded text-xs font-medium ${TYPE_COLORS[directive.type]}`}>
                            {directive.type}
                          </div>
                          <div>
                            <p className="text-sm text-gray-500">Effective: {directive.effectiveDate}</p>
                          </div>
                        </div>
                        <div className="flex items-center gap-2">
                          <StatusIcon className={`w-4 h-4 ${statusInfo.bg.includes("green") ? "text-green-600" : statusInfo.bg.includes("amber") ? "text-amber-600" : "text-red-600"}`} />
                          <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${statusInfo.bg}`}>{directive.status}</span>
                        </div>
                      </button>

                      {isExpanded && (
                        <div className="border-t border-gray-200 px-5 py-4 space-y-3">
                          <p className="text-sm text-gray-700">{directive.summary}</p>
                          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-xs">
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
                              <button className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-blue-600 border border-blue-200 rounded-lg hover:bg-blue-50 transition-colors">
                                <Eye className="w-3 h-3" /> View Document
                              </button>
                            )}
                            <button className="px-3 py-1.5 text-xs font-medium text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
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
