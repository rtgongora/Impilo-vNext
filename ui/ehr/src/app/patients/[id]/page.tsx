"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type Journey } from "@/lib/apiClient";
import type { Patient } from "@/stores/ehrStore";

const JOURNEY_STATE_COLORS: Record<string, string> = {
  REGISTERED: "bg-blue-50 text-blue-700",
  TRIAGED: "bg-purple-50 text-purple-700",
  WAITING: "bg-amber-50 text-amber-700",
  IN_CONSULTATION: "bg-green-50 text-green-700",
  REFERRED: "bg-cyan-50 text-cyan-700",
  LAB_ORDERED: "bg-indigo-50 text-indigo-700",
  PHARMACY: "bg-teal-50 text-teal-700",
  IN_PROCEDURE: "bg-orange-50 text-orange-700",
  OBSERVATION: "bg-yellow-50 text-yellow-700",
  DECISION: "bg-pink-50 text-pink-700",
  DISCHARGE_PENDING: "bg-lime-50 text-lime-700",
  DISCHARGED: "bg-gray-50 text-gray-600",
  CANCELLED: "bg-red-50 text-red-600",
};

function JourneyRow({ journey }: { journey: Journey }) {
  const colorClass =
    JOURNEY_STATE_COLORS[journey.state] ?? "bg-gray-50 text-gray-600";

  return (
    <tr className="border-b border-gray-100 hover:bg-gray-50">
      <td className="py-3 px-4 text-xs font-mono text-gray-500">
        {journey.id.slice(0, 8)}…
      </td>
      <td className="py-3 px-4">
        <span
          className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${colorClass}`}
        >
          {journey.state.replace(/_/g, " ")}
        </span>
      </td>
      <td className="py-3 px-4 text-sm text-gray-600">
        {journey.chiefComplaint ?? "—"}
      </td>
      <td className="py-3 px-4 text-sm text-gray-500">
        {new Date(journey.startedAt).toLocaleDateString("en-ZW")}
      </td>
      <td className="py-3 px-4 text-sm text-gray-500">
        {new Date(journey.updatedAt).toLocaleTimeString("en-ZW", {
          hour: "2-digit",
          minute: "2-digit",
        })}
      </td>
    </tr>
  );
}

export default function PatientDetailPage() {
  const params = useParams<{ id: string }>();
  const cpid = params.id;

  const patient = useQuery<Patient>({
    queryKey: ["patient", cpid],
    queryFn: () => apiClient.getPatient(cpid),
    enabled: Boolean(cpid),
  });

  const journeys = useQuery<Journey[]>({
    queryKey: ["patient-journeys", cpid],
    queryFn: () => apiClient.getJourneyTimeline(cpid),
    enabled: Boolean(cpid),
  });

  const encounters = useQuery({
    queryKey: ["encounters", cpid],
    queryFn: () => apiClient.getEncounters(cpid),
    enabled: Boolean(cpid),
  });

  if (patient.isLoading) {
    return (
      <div className="min-h-screen bg-impilo-surface flex items-center justify-center">
        <p className="text-gray-400">Loading patient record…</p>
      </div>
    );
  }

  if (patient.isError || !patient.data) {
    return (
      <div className="min-h-screen bg-impilo-surface flex items-center justify-center">
        <div className="text-center">
          <p className="text-impilo-danger font-medium">Patient not found</p>
          <Link
            href="/"
            className="mt-3 inline-block text-sm text-impilo-primary hover:underline"
          >
            ← Back to search
          </Link>
        </div>
      </div>
    );
  }

  const p = patient.data;

  return (
    <div className="min-h-screen bg-impilo-surface">
      <header className="bg-impilo-primary text-white px-6 py-3 flex items-center justify-between">
        <div className="flex items-center gap-6">
          <h1 className="text-lg font-semibold">Impilo EHR</h1>
          <nav className="flex gap-4 text-sm">
            <Link href="/dashboard" className="opacity-80 hover:opacity-100">
              Dashboard
            </Link>
            <Link href="/encounters" className="opacity-80 hover:opacity-100">
              Encounters
            </Link>
            <Link href="/orders" className="opacity-80 hover:opacity-100">
              Orders
            </Link>
            <Link href="/results" className="opacity-80 hover:opacity-100">
              Results
            </Link>
          </nav>
        </div>
      </header>

      <div className="bg-white border-b px-6 py-4">
        <div className="max-w-5xl mx-auto flex items-start gap-5">
          <div className="w-12 h-12 rounded-full bg-impilo-primary text-white flex items-center justify-center font-semibold text-lg shrink-0">
            {p.firstName[0]}
            {p.lastName[0]}
          </div>
          <div className="flex-1">
            <h2 className="text-xl font-semibold text-gray-900">
              {p.lastName}, {p.firstName}
            </h2>
            <div className="flex flex-wrap gap-4 text-sm text-gray-600 mt-1">
              <span>{p.gender}</span>
              <span>DOB: {p.dateOfBirth}</span>
              <span className="font-mono">CPID: {p.cpid}</span>
              {p.nationalId && <span>NID: {p.nationalId}</span>}
            </div>
            {p.allergies.length > 0 && (
              <div className="mt-2">
                <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-800">
                  Allergies: {p.allergies.join(", ")}
                </span>
              </div>
            )}
          </div>
        </div>
      </div>

      <main className="max-w-5xl mx-auto px-6 py-6 space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <h3 className="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-3">
              Active Conditions
            </h3>
            {p.conditions.length > 0 ? (
              <ul className="space-y-1">
                {p.conditions.map((c, i) => (
                  <li
                    key={i}
                    className="text-sm px-2 py-1 bg-yellow-50 border border-yellow-200 rounded"
                  >
                    {c}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-gray-400">No active conditions</p>
            )}
          </div>

          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <h3 className="text-sm font-semibold text-gray-700 uppercase tracking-wide mb-3">
              Recent Encounters
            </h3>
            {encounters.isLoading ? (
              <p className="text-sm text-gray-400">Loading…</p>
            ) : encounters.data && encounters.data.length > 0 ? (
              <ul className="space-y-2">
                {encounters.data.slice(0, 5).map((enc) => (
                  <li key={enc.id} className="text-sm flex items-center justify-between">
                    <span className="text-gray-700">{enc.encounterType}</span>
                    <span className="text-gray-400 text-xs">
                      {new Date(enc.startedAt).toLocaleDateString("en-ZW")}
                    </span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-gray-400">No encounters on record</p>
            )}
          </div>
        </div>

        <div className="bg-white rounded-lg border border-gray-200">
          <div className="px-4 py-3 border-b border-gray-100">
            <h3 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">
              Journey Timeline
            </h3>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-100 bg-gray-50">
                  <th className="text-left py-2 px-4 font-medium text-gray-500">Journey ID</th>
                  <th className="text-left py-2 px-4 font-medium text-gray-500">State</th>
                  <th className="text-left py-2 px-4 font-medium text-gray-500">Chief Complaint</th>
                  <th className="text-left py-2 px-4 font-medium text-gray-500">Started</th>
                  <th className="text-left py-2 px-4 font-medium text-gray-500">Last Update</th>
                </tr>
              </thead>
              <tbody>
                {journeys.isLoading ? (
                  <tr>
                    <td colSpan={5} className="py-8 text-center text-gray-400">
                      Loading journey timeline…
                    </td>
                  </tr>
                ) : journeys.data && journeys.data.length > 0 ? (
                  journeys.data.map((j) => <JourneyRow key={j.id} journey={j} />)
                ) : (
                  <tr>
                    <td colSpan={5} className="py-8 text-center text-gray-400">
                      No journeys found for this patient
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </main>
    </div>
  );
}
