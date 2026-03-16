"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/apiClient";
import type { Patient } from "@/stores/ehrStore";

export function ClinicalDashboard({ patient }: { patient: Patient }) {
  const encounters = useQuery({
    queryKey: ["encounters", patient.cpid],
    queryFn: () => apiClient.getEncounters(patient.cpid),
  });

  const vitals = useQuery({
    queryKey: ["vitals", patient.cpid],
    queryFn: () => apiClient.getLatestVitals(patient.cpid),
  });

  const medications = useQuery({
    queryKey: ["medications", patient.cpid],
    queryFn: () => apiClient.getActiveMedications(patient.cpid),
  });

  const labResults = useQuery({
    queryKey: ["lab-results", patient.cpid],
    queryFn: () => apiClient.getLabResults(patient.cpid),
  });

  return (
    <div className="grid grid-cols-2 gap-4">
      {/* Active Conditions */}
      <div className="bg-white rounded-lg border p-4">
        <h3 className="font-semibold text-sm text-gray-700 mb-3 uppercase">
          Active Conditions
        </h3>
        {patient.conditions.length > 0 ? (
          <ul className="space-y-2">
            {patient.conditions.map((condition, i) => (
              <li
                key={i}
                className="text-sm px-2 py-1 bg-yellow-50 border border-yellow-200 rounded"
              >
                {condition}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-gray-400">No active conditions</p>
        )}
      </div>

      {/* Latest Vitals */}
      <div className="bg-white rounded-lg border p-4">
        <h3 className="font-semibold text-sm text-gray-700 mb-3 uppercase">
          Latest Vitals
        </h3>
        {vitals.data ? (
          <div className="grid grid-cols-2 gap-2">
            {vitals.data.map((vital, i) => (
              <div key={i} className="text-sm">
                <span className="text-gray-500">{vital.type}: </span>
                <span className="font-medium">
                  {vital.value} {vital.unit}
                </span>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm text-gray-400">
            {vitals.isLoading ? "Loading..." : "No vitals recorded"}
          </p>
        )}
      </div>

      {/* Active Medications */}
      <div className="bg-white rounded-lg border p-4">
        <h3 className="font-semibold text-sm text-gray-700 mb-3 uppercase">
          Active Medications
        </h3>
        {medications.data && medications.data.length > 0 ? (
          <ul className="space-y-2">
            {medications.data.map((med, i) => (
              <li key={i} className="text-sm">
                <div className="font-medium">{med.medication}</div>
                <div className="text-gray-500">
                  {med.dosage} — {med.frequency}
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-gray-400">
            {medications.isLoading ? "Loading..." : "No active medications"}
          </p>
        )}
      </div>

      {/* Recent Lab Results */}
      <div className="bg-white rounded-lg border p-4">
        <h3 className="font-semibold text-sm text-gray-700 mb-3 uppercase">
          Recent Lab Results
        </h3>
        {labResults.data && labResults.data.length > 0 ? (
          <ul className="space-y-2">
            {labResults.data.map((result, i) => (
              <li key={i} className="text-sm">
                <div className="flex justify-between">
                  <span className="font-medium">{result.testName}</span>
                  <span
                    className={
                      result.abnormal ? "text-red-600 font-bold" : "text-green-600"
                    }
                  >
                    {result.value} {result.unit}
                  </span>
                </div>
                <div className="text-gray-400 text-xs">{result.date}</div>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-gray-400">
            {labResults.isLoading ? "Loading..." : "No recent results"}
          </p>
        )}
      </div>

      {/* Recent Encounters */}
      <div className="bg-white rounded-lg border p-4 col-span-2">
        <h3 className="font-semibold text-sm text-gray-700 mb-3 uppercase">
          Recent Encounters
        </h3>
        {encounters.data && encounters.data.length > 0 ? (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500 border-b">
                <th className="pb-2">Date</th>
                <th className="pb-2">Type</th>
                <th className="pb-2">Chief Complaint</th>
                <th className="pb-2">Status</th>
              </tr>
            </thead>
            <tbody>
              {encounters.data.slice(0, 10).map((enc) => (
                <tr key={enc.id} className="border-b last:border-0">
                  <td className="py-2">
                    {new Date(enc.startedAt).toLocaleDateString()}
                  </td>
                  <td className="py-2">{enc.encounterType}</td>
                  <td className="py-2">{enc.chiefComplaint || "—"}</td>
                  <td className="py-2">
                    <span
                      className={`px-2 py-0.5 rounded-full text-xs ${
                        enc.status === "COMPLETED"
                          ? "bg-green-100 text-green-700"
                          : enc.status === "IN_PROGRESS"
                          ? "bg-blue-100 text-blue-700"
                          : "bg-gray-100 text-gray-600"
                      }`}
                    >
                      {enc.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <p className="text-sm text-gray-400">
            {encounters.isLoading ? "Loading..." : "No encounters found"}
          </p>
        )}
      </div>
    </div>
  );
}
