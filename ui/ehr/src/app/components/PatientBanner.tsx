"use client";

import { useEhrStore, type Patient } from "@/stores/ehrStore";

export function PatientBanner({ patient }: { patient: Patient }) {
  const { startEncounter } = useEhrStore();
  const age = calculateAge(patient.dateOfBirth);

  return (
    <div className="bg-white border-b px-6 py-3 flex items-center justify-between">
      <div className="flex items-center gap-6">
        <div className="w-12 h-12 rounded-full bg-impilo-primary text-white flex items-center justify-center font-semibold text-lg">
          {patient.firstName[0]}
          {patient.lastName[0]}
        </div>
        <div>
          <h2 className="font-semibold text-lg">
            {patient.lastName}, {patient.firstName}
          </h2>
          <div className="text-sm text-gray-600 flex gap-4">
            <span>{patient.gender}</span>
            <span>{age} years</span>
            <span>DOB: {patient.dateOfBirth}</span>
            <span>CPID: {patient.cpid}</span>
            {patient.nationalId && <span>NID: {patient.nationalId}</span>}
          </div>
        </div>
      </div>

      <div className="flex items-center gap-3">
        {patient.allergies.length > 0 && (
          <div className="bg-red-100 text-red-800 px-3 py-1 rounded-full text-xs font-medium">
            Allergies: {patient.allergies.join(", ")}
          </div>
        )}
        <div className="flex gap-2">
          <button
            onClick={() => startEncounter(patient.cpid, "OPD")}
            className="px-3 py-1.5 bg-impilo-secondary text-white rounded-md text-sm hover:bg-green-700"
          >
            New OPD Visit
          </button>
          <button
            onClick={() => startEncounter(patient.cpid, "EMERGENCY")}
            className="px-3 py-1.5 bg-impilo-danger text-white rounded-md text-sm hover:bg-red-700"
          >
            Emergency
          </button>
        </div>
      </div>
    </div>
  );
}

function calculateAge(dob: string): number {
  const birth = new Date(dob);
  const now = new Date();
  let age = now.getFullYear() - birth.getFullYear();
  const monthDiff = now.getMonth() - birth.getMonth();
  if (monthDiff < 0 || (monthDiff === 0 && now.getDate() < birth.getDate())) {
    age--;
  }
  return age;
}
