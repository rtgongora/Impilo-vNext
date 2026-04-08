"use client";

/**
 * Client registry administration — MPI-oriented patient directory via `/internal/v1/patients`.
 * Route: /registry/clients | guard: REGISTRY_ADMIN
 *
 * Distinct from queue search (shift-guarded): registry operators reconcile CPID / demographics without shift.
 */

import { type FormEvent, useState } from "react";
import Link from "next/link";
import { ArrowLeft, Loader2, Search, User } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { RegistryPlaneContextBar } from "@/components/experience/RegistryPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { usePatients, type PatientResource } from "@/hooks/queries/usePatients";
import { getPatientDisplayName, getPatientQueueSummary } from "@/lib/queue-workflows";

export default function RegistryClientDirectoryPage() {
  const [searchTerm, setSearchTerm] = useState("");
  const [submitted, setSubmitted] = useState("");
  const { data, isLoading } = usePatients(submitted ? { search: submitted } : undefined);
  const patients = submitted ? (data?.data ?? []) : [];

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitted(searchTerm);
  }

  return (
    <AppLayout>
      <PageShell
        title="Client registry"
        subtitle="Patient index search for registry governance — backed by the live patient API"
      >
        <RegistryPlaneContextBar preferStore />

        <div className="mb-4">
          <Link
            href="/registry-admin"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="h-4 w-4" /> Back to registry administration
          </Link>
        </div>

        <div className="mb-4 rounded-lg border border-slate-200 bg-white p-4">
          <p className="text-xs text-slate-600 mb-3">
            Search the longitudinal patient directory by name, CPID, or other fields supported by the API. Opening
            the full clinical chart still follows Facility Work sequencing (facility → workspace → shift).
          </p>
          <form onSubmit={onSubmit} className="flex flex-wrap gap-2">
            <div className="relative min-w-[240px] flex-1">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
              <input
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Search patients…"
                className="w-full rounded-lg border border-gray-300 py-2.5 pl-10 pr-4 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <button
              type="submit"
              className="rounded-lg bg-blue-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-blue-700"
            >
              Search directory
            </button>
          </form>
        </div>

        {isLoading && (
          <div className="flex items-center gap-2 text-sm text-gray-500">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading…
          </div>
        )}

        {submitted && !isLoading && patients.length === 0 && (
          <div className="rounded-lg border border-gray-200 bg-white p-10 text-center">
            <User className="mx-auto mb-2 h-10 w-10 text-gray-300" />
            <p className="text-sm text-gray-500">No patients match &ldquo;{submitted}&rdquo;</p>
          </div>
        )}

        {patients.length > 0 && (
          <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="px-4 py-3 text-left font-medium text-gray-600">Patient</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">CPID</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">DOB</th>
                  <th className="px-4 py-3 text-right font-medium text-gray-600">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {patients.map((patient: PatientResource) => (
                  <tr key={patient.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <span className="font-medium text-gray-900">{getPatientDisplayName(patient)}</span>
                      <p className="text-xs text-gray-500">{getPatientQueueSummary(patient)}</p>
                    </td>
                    <td className="px-4 py-3 font-mono text-xs text-gray-700">{patient.attributes.cpid}</td>
                    <td className="px-4 py-3 text-gray-600">{patient.attributes.dateOfBirth}</td>
                    <td className="px-4 py-3 text-right">
                      <Link
                        href={`/ehr/${patient.id}?entry=registry`}
                        className="text-xs font-medium text-blue-600 hover:text-blue-800"
                      >
                        Open chart (requires shift)
                      </Link>
                      <span className="mx-2 text-gray-300">|</span>
                      <Link
                        href="/id-services?from=registry-admin"
                        className="text-xs font-medium text-amber-800 hover:text-amber-950"
                      >
                        Identity services
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
