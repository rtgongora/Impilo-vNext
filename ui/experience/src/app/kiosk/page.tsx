"use client";

/**
 * Self-Service Kiosk — Patient check-in terminal.
 * Route: /kiosk
 *
 * Allows patients to check themselves in by entering their ID or name.
 * Creates a queue entry automatically upon successful lookup.
 */

import { useState, type FormEvent } from "react";
import { Search, Loader2, CheckCircle2, UserPlus, AlertCircle } from "lucide-react";
import { useMutation } from "@tanstack/react-query";
import { usePatients } from "@/hooks/queries/usePatients";
import { apiClient } from "@/lib/api-client";
import { useFacilityStore } from "@/hooks/useFacilityStore";

export default function KioskPage() {
  const facility = useFacilityStore((s) => s.facility);
  const [searchTerm, setSearchTerm] = useState("");
  const [searchSubmitted, setSearchSubmitted] = useState("");
  const [checkedIn, setCheckedIn] = useState(false);
  const [checkedInName, setCheckedInName] = useState("");

  const { data: patientsData, isLoading: searching } = usePatients(
    searchSubmitted ? { search: searchSubmitted } : undefined,
  );
  const patients = searchSubmitted ? (patientsData?.data ?? []) : [];

  const checkIn = useMutation({
    mutationFn: (body: { patient_id: string; facility_id: string; queue_type: string; priority: string }) =>
      apiClient.post("/internal/v1/queue/entries", body),
    onSuccess: () => setCheckedIn(true),
  });

  function handleSearch(e: FormEvent) {
    e.preventDefault();
    setSearchSubmitted(searchTerm.trim());
    setCheckedIn(false);
  }

  function handleCheckIn(patientId: string, patientName: string) {
    if (!facility) return;
    setCheckedInName(patientName);
    checkIn.mutate({
      patient_id: patientId,
      facility_id: facility.id,
      queue_type: "WALK_IN",
      priority: "NORMAL",
    });
  }

  if (checkedIn) {
    return (
      <div className="min-h-screen bg-green-50 flex items-center justify-center p-8">
        <div className="max-w-md w-full text-center">
          <CheckCircle2 className="w-20 h-20 text-green-500 mx-auto mb-6" />
          <h1 className="text-3xl font-bold text-gray-900 mb-2">Check-In Complete</h1>
          <p className="text-lg text-gray-600 mb-2">{checkedInName}</p>
          <p className="text-sm text-gray-500 mb-8">
            You have been added to the queue at {facility?.name}. Please wait to be called.
          </p>
          <button onClick={() => { setCheckedIn(false); setSearchTerm(""); setSearchSubmitted(""); }}
            className="px-8 py-3 bg-green-600 text-white text-lg font-medium rounded-xl hover:bg-green-700 transition-colors">
            Next Patient
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col items-center justify-center p-8">
      <div className="max-w-lg w-full">
        <div className="text-center mb-8">
          <UserPlus className="w-16 h-16 text-impilo-500 mx-auto mb-4" />
          <h1 className="text-3xl font-bold text-gray-900">Self Check-In</h1>
          <p className="text-gray-500 mt-2">
            {facility ? facility.name : "Welcome"}
          </p>
        </div>

        {!facility ? (
          <div className="bg-white rounded-xl border border-gray-200 p-8 text-center">
            <AlertCircle className="w-10 h-10 text-amber-500 mx-auto mb-3" />
            <p className="text-gray-600">Kiosk not configured. Please set up facility context.</p>
          </div>
        ) : (
          <>
            <form onSubmit={handleSearch} className="mb-6">
              <div className="flex gap-3">
                <input
                  type="text"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  placeholder="Enter your name or ID number"
                  className="flex-1 px-5 py-4 text-lg border-2 border-gray-300 rounded-xl focus:border-impilo-400 focus:ring-2 focus:ring-impilo-200"
                  autoFocus
                />
                <button type="submit"
                  className="px-6 py-4 bg-impilo-500 text-white rounded-xl hover:bg-impilo-600 transition-colors">
                  <Search className="w-6 h-6" />
                </button>
              </div>
            </form>

            {searching && (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="w-8 h-8 animate-spin text-gray-400" />
              </div>
            )}

            {patients.length > 0 && (
              <div className="space-y-3">
                {patients.map((patient) => (
                  <button key={patient.id}
                    onClick={() => handleCheckIn(patient.id, patient.attributes.displayName ?? patient.attributes.givenName ?? "Patient")}
                    disabled={checkIn.isPending}
                    className="w-full text-left bg-white rounded-xl border-2 border-gray-200 p-5 hover:border-impilo-400 hover:shadow-md transition-all">
                    <p className="text-lg font-semibold text-gray-900">
                      {patient.attributes.displayName ?? `${patient.attributes.givenName} ${patient.attributes.familyName}`}
                    </p>
                    <p className="text-sm text-gray-500 mt-1">
                      DOB: {patient.attributes.dateOfBirth ?? "—"} · {patient.attributes.gender ?? "—"}
                    </p>
                  </button>
                ))}
              </div>
            )}

            {searchSubmitted && !searching && patients.length === 0 && (
              <div className="bg-white rounded-xl border border-gray-200 p-8 text-center">
                <p className="text-gray-500">No patients found for &quot;{searchSubmitted}&quot;</p>
                <p className="text-sm text-gray-400 mt-1">Please check your name or ID and try again.</p>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
