"use client";

/**
 * Patient Search — Search across all patients by name, CPID, or DOB.
 * Route: /queue/search | pageTitle: "Patient Search"
 */

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { ArrowLeft, ArrowRightLeft, Loader2, Search, User, UserPlus } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { QueueWorkspaceHeader } from "@/components/queue/QueueWorkspaceHeader";
import { usePatients, type PatientResource } from "@/hooks/queries/usePatients";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { getPatientDisplayName, getPatientQueueSummary } from "@/lib/queue-workflows";

export default function PatientSearchPage() {
  const facility = useFacilityStore((state) => state.facility);
  const [searchTerm, setSearchTerm] = useState("");
  const [searchSubmitted, setSearchSubmitted] = useState("");

  const { data: patientsData, isLoading } = usePatients(
    searchSubmitted ? { search: searchSubmitted } : undefined,
  );

  const patients = searchSubmitted ? (patientsData?.data ?? []) : [];

  function handleSearch(e: FormEvent) {
    e.preventDefault();
    setSearchSubmitted(searchTerm);
  }

  return (
    <AppLayout>
      <PageShell title="Patient Search" subtitle={facility ? `${facility.name}` : "Search by name, CPID, national ID, or date of birth"}>
        <div className="space-y-6">
          <QueueWorkspaceHeader
            badge="Queue search"
            badgeIcon={Search}
            title="Find the right patient, then route directly into chart or registration"
            description="Search now carries the active facility context so the queue team can decide whether to open the chart, register a walk-in, or continue searching without losing the thread."
            facilityName={facility?.name}
            actions={[
              { href: "/queue", label: "Queue Workboard", icon: ArrowRightLeft },
              { href: "/queue/walk-in", label: "Walk-in Registration", icon: UserPlus, tone: "secondary" },
            ]}
            metrics={[
              {
                label: "Results",
                value: String(patients.length),
                detail: searchSubmitted ? `Matches for "${searchSubmitted}" in the patient directory.` : "Search the longitudinal record before creating a new queue visit.",
              },
            ]}
          />

          <Link href="/queue" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors">
            <ArrowLeft className="w-4 h-4" /> Back to queue
          </Link>

          <div className="max-w-4xl space-y-6">
          <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
            <form onSubmit={handleSearch} className="flex gap-2">
              <div className="relative flex-1">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <input
                  type="text"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  placeholder="Search by name, CPID, national ID, or date of birth..."
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                />
              </div>
              <button type="submit" className="px-6 py-2.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors">
                Search
              </button>
            </form>
          </div>

          {isLoading && (
            <div className="flex items-center gap-2 text-sm text-gray-500">
              <Loader2 className="w-4 h-4 animate-spin" /> Searching...
            </div>
          )}

          {searchSubmitted && !isLoading && patients.length === 0 && (
            <div className="rounded-3xl border border-slate-200 bg-white p-12 text-center shadow-sm">
              <User className="mx-auto mb-3 h-10 w-10 text-slate-300" />
              <p className="text-sm text-slate-500">No patients found for &ldquo;{searchSubmitted}&rdquo;</p>
              <Link href="/queue/walk-in" className="mt-3 inline-block text-sm text-blue-600 hover:text-blue-800">
                Register new patient
              </Link>
            </div>
          )}

          {patients.length > 0 && (
            <div className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b bg-gray-50">
                    <th className="text-left px-4 py-3 font-medium text-gray-600">Patient</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">CPID</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">DOB</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">Gender</th>
                    <th className="text-right px-4 py-3 font-medium text-gray-600">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {patients.map((patient: PatientResource) => (
                    <tr key={patient.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-3">
                          <div className="w-8 h-8 rounded-full bg-blue-100 flex items-center justify-center">
                            <User className="w-4 h-4 text-blue-600" />
                          </div>
                          <div>
                            <span className="font-medium text-gray-900">{getPatientDisplayName(patient)}</span>
                            <p className="mt-1 text-xs text-slate-500">{getPatientQueueSummary(patient)}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-gray-600 font-mono text-xs">{patient.attributes.cpid}</td>
                      <td className="px-4 py-3 text-gray-600">{patient.attributes.dateOfBirth}</td>
                      <td className="px-4 py-3 text-gray-600 capitalize">{patient.attributes.gender}</td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex justify-end gap-2">
                          <Link
                            href={`/ehr/${patient.id}?entry=search`}
                            className="inline-block rounded-xl bg-blue-600 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-blue-700"
                          >
                            Open Chart
                          </Link>
                          <Link
                            href={`/queue/walk-in?patientId=${patient.id}`}
                            className="inline-block rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-50"
                          >
                            Add to Queue
                          </Link>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
