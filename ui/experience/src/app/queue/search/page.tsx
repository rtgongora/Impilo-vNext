"use client";

/**
 * Patient Search — Search across all patients by name, CPID, or DOB.
 * Route: /queue/search | pageTitle: "Patient Search"
 */

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { Search, Loader2, User, ArrowLeft } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { usePatients, type PatientResource } from "@/hooks/queries/usePatients";

export default function PatientSearchPage() {
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
      <PageShell title="Patient Search" subtitle="Search by name, CPID, national ID, or date of birth">
        <div className="mb-4">
          <Link href="/queue" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors">
            <ArrowLeft className="w-4 h-4" /> Back to queue
          </Link>
        </div>

        <div className="max-w-3xl space-y-6">
          <div className="bg-white rounded-lg border border-gray-200 p-5">
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
            <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
              <User className="w-10 h-10 text-gray-300 mx-auto mb-3" />
              <p className="text-gray-400 text-sm">No patients found for &ldquo;{searchSubmitted}&rdquo;</p>
              <Link href="/queue/walk-in" className="mt-3 inline-block text-sm text-blue-600 hover:text-blue-800">
                Register new patient
              </Link>
            </div>
          )}

          {patients.length > 0 && (
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
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
                          <span className="font-medium text-gray-900">{patient.attributes.displayName}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-gray-600 font-mono text-xs">{patient.attributes.cpid}</td>
                      <td className="px-4 py-3 text-gray-600">{patient.attributes.dateOfBirth}</td>
                      <td className="px-4 py-3 text-gray-600 capitalize">{patient.attributes.gender}</td>
                      <td className="px-4 py-3 text-right">
                        <Link href={`/ehr/${patient.id}`}
                          className="px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-md hover:bg-blue-700 transition-colors inline-block">
                          Open Chart
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
