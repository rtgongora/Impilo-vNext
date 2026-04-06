"use client";

/**
 * Procedures — View past and scheduled procedures with filtering.
 * Route: /ehr/[patientId]/procedures | pageTitle: "Procedures"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import { Scissors, Loader2, Filter, Calendar } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

interface Procedure {
  id: string;
  name: string;
  type: string;
  date: string;
  surgeon: string;
  facility: string;
  status: "Completed" | "Scheduled" | "Cancelled" | "In Progress";
  notes: string;
}

const MOCK_PROCEDURES: Procedure[] = [
  { id: "pr-1", name: "Appendectomy", type: "General Surgery", date: "2026-03-10", surgeon: "Dr. Mutasa", facility: "Parirenyatwa Hospital", status: "Completed", notes: "Laparoscopic approach, no complications" },
  { id: "pr-2", name: "Colonoscopy", type: "Endoscopy", date: "2026-04-15", surgeon: "Dr. Chikwava", facility: "Harare Central Hospital", status: "Scheduled", notes: "Screening colonoscopy, bowel prep instructions given" },
  { id: "pr-3", name: "Knee Arthroscopy", type: "Orthopaedic", date: "2025-11-20", surgeon: "Dr. Ndlovu", facility: "Avenues Clinic", status: "Completed", notes: "Meniscal repair, weight-bearing as tolerated" },
  { id: "pr-4", name: "Cataract Extraction", type: "Ophthalmology", date: "2025-08-05", surgeon: "Dr. Sibanda", facility: "Parirenyatwa Hospital", status: "Completed", notes: "Phacoemulsification with IOL implant, left eye" },
  { id: "pr-5", name: "CT Abdomen Guided Biopsy", type: "Radiology", date: "2026-05-01", surgeon: "Dr. Moyo", facility: "Harare Central Hospital", status: "Scheduled", notes: "Liver lesion biopsy, NPO from midnight" },
  { id: "pr-6", name: "Tonsillectomy", type: "ENT", date: "2026-02-14", surgeon: "Dr. Mutasa", facility: "Avenues Clinic", status: "Cancelled", notes: "Patient declined procedure" },
];

const STATUS_STYLES: Record<string, string> = {
  Completed: "bg-green-100 text-green-700",
  Scheduled: "bg-blue-100 text-blue-700",
  Cancelled: "bg-red-100 text-red-700",
  "In Progress": "bg-amber-100 text-amber-700",
};

const PROCEDURE_TYPES = ["All", "General Surgery", "Endoscopy", "Orthopaedic", "Ophthalmology", "Radiology", "ENT"];

export default function ProceduresPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { data, isLoading } = useQuery({
    queryKey: ["procedures", patientId],
    queryFn: async () => {
      try {
        return await apiClient.get(`/patients/${patientId}/procedures`);
      } catch {
        return { data: MOCK_PROCEDURES };
      }
    },
  });

  const procedures: Procedure[] = (data as { data: Procedure[] })?.data ?? MOCK_PROCEDURES;

  const [typeFilter, setTypeFilter] = useState("All");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [showFilters, setShowFilters] = useState(false);

  const filtered = procedures.filter((p) => {
    if (typeFilter !== "All" && p.type !== typeFilter) return false;
    if (dateFrom && p.date < dateFrom) return false;
    if (dateTo && p.date > dateTo) return false;
    return true;
  });

  return (
    <EHRLayout>
      <PageShell title="Procedures" subtitle="Past and scheduled procedure history">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading procedures...</span>
          </div>
        ) : (
          <div className="space-y-6">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Scissors className="w-5 h-5 text-purple-600" />
                <h2 className="text-lg font-semibold text-gray-900">Procedure History</h2>
                <span className="text-sm text-gray-500">({filtered.length})</span>
              </div>
              <button
                type="button"
                onClick={() => setShowFilters((prev) => !prev)}
                className="inline-flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
              >
                <Filter className="w-4 h-4" />
                Filters
              </button>
            </div>

            {showFilters && (
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Procedure Type</label>
                    <select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                      {PROCEDURE_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">From Date</label>
                    <input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">To Date</label>
                    <input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                  </div>
                </div>
              </div>
            )}

            {filtered.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <Scissors className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No procedures found</p>
              </div>
            ) : (
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-gray-200 bg-gray-50">
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Date</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Procedure</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Type</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Surgeon</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Facility</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filtered.map((proc) => (
                        <tr key={proc.id} className="border-b border-gray-100 hover:bg-gray-50 transition-colors">
                          <td className="px-4 py-3 text-gray-900 flex items-center gap-1.5">
                            <Calendar className="w-3.5 h-3.5 text-gray-400" />
                            {proc.date}
                          </td>
                          <td className="px-4 py-3">
                            <div className="font-medium text-gray-900">{proc.name}</div>
                            <div className="text-xs text-gray-500">{proc.notes}</div>
                          </td>
                          <td className="px-4 py-3 text-gray-700">{proc.type}</td>
                          <td className="px-4 py-3 text-gray-700">{proc.surgeon}</td>
                          <td className="px-4 py-3 text-gray-700">{proc.facility}</td>
                          <td className="px-4 py-3">
                            <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_STYLES[proc.status]}`}>{proc.status}</span>
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
