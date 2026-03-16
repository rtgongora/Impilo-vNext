"use client";

import { useState } from "react";
import { useEhrStore, type Patient } from "@/stores/ehrStore";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/apiClient";

export function PatientSearch() {
  const [query, setQuery] = useState("");
  const { recentPatients, setActivePatient, addRecentPatient } = useEhrStore();

  const searchResults = useQuery({
    queryKey: ["patient-search", query],
    queryFn: () => apiClient.searchPatients(query),
    enabled: query.length >= 2,
    staleTime: 10_000,
  });

  function selectPatient(patient: Patient) {
    setActivePatient(patient);
    addRecentPatient(patient);
  }

  return (
    <div className="space-y-4">
      <div>
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by name, NID, or CPID..."
          className="w-full px-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-impilo-primary"
        />
      </div>

      {searchResults.data && searchResults.data.length > 0 && (
        <div className="space-y-1">
          <h3 className="text-xs font-semibold text-gray-500 uppercase">
            Search Results
          </h3>
          {searchResults.data.map((patient) => (
            <PatientRow
              key={patient.cpid}
              patient={patient}
              onClick={() => selectPatient(patient)}
            />
          ))}
        </div>
      )}

      {query.length < 2 && recentPatients.length > 0 && (
        <div className="space-y-1">
          <h3 className="text-xs font-semibold text-gray-500 uppercase">
            Recent Patients
          </h3>
          {recentPatients.map((patient) => (
            <PatientRow
              key={patient.cpid}
              patient={patient}
              onClick={() => selectPatient(patient)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function PatientRow({
  patient,
  onClick,
}: {
  patient: Patient;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className="w-full text-left px-3 py-2 rounded-md hover:bg-blue-50 transition-colors"
    >
      <div className="font-medium text-sm">
        {patient.lastName}, {patient.firstName}
      </div>
      <div className="text-xs text-gray-500">
        {patient.gender} | DOB: {patient.dateOfBirth} | CPID: {patient.cpid}
      </div>
    </button>
  );
}
