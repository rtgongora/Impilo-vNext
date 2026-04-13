"use client";

/**
 * EncounterPatientHeader -- Compact patient header with demographics, MRN,
 * age, gender, location badge, encounter metadata (type, duration, provider).
 *
 * Uses mock data; production would pull from the encounter context / VITO.
 */

import { User, Stethoscope, MapPin, Calendar } from "lucide-react";

// ---------------------------------------------------------------------------
// Mock data
// ---------------------------------------------------------------------------

const MOCK_PATIENT = {
  name: "Mary N. Johnson",
  mrn: "MRN-2026-00471",
  gender: "female" as "male" | "female" | "other",
  dateOfBirth: "1961-03-14",
  ward: "Medical Ward 2",
  bed: "Bed 14A",
};

const MOCK_ENCOUNTER = {
  type: "inpatient" as "inpatient" | "outpatient" | "emergency",
  status: "active" as "active" | "completed",
  admissionDate: "2026-04-02",
};

const MOCK_PRIMARY_DIAGNOSIS = {
  name: "Urinary tract infection",
  icdCode: "N39.0",
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function calculateAge(dob: string): number {
  const birth = new Date(dob);
  const now = new Date();
  let age = now.getFullYear() - birth.getFullYear();
  const m = now.getMonth() - birth.getMonth();
  if (m < 0 || (m === 0 && now.getDate() < birth.getDate())) age--;
  return age;
}

function daysBetween(a: string, b: Date): number {
  return Math.floor(
    (b.getTime() - new Date(a).getTime()) / (1000 * 60 * 60 * 24),
  );
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  const months = [
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
  ];
  return `${String(d.getDate()).padStart(2, "0")} ${months[d.getMonth()]} ${d.getFullYear()}`;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function EncounterPatientHeader() {
  const patient = MOCK_PATIENT;
  const encounter = MOCK_ENCOUNTER;
  const age = calculateAge(patient.dateOfBirth);
  const los = daysBetween(encounter.admissionDate, new Date());
  const primaryDiagnosis = MOCK_PRIMARY_DIAGNOSIS;

  const genderBadgeClasses =
    patient.gender === "female"
      ? "bg-pink-50 text-pink-600 border-pink-200"
      : patient.gender === "male"
        ? "bg-impilo-50 text-impilo-500 border-impilo-200"
        : "bg-purple-50 text-purple-600 border-purple-200";

  const genderLabel =
    patient.gender === "female" ? "F" : patient.gender === "male" ? "M" : "O";

  return (
    <div className="bg-white border-b border-gray-200 px-4 py-2">
      <div className="flex items-center justify-between">
        {/* Left: avatar + demographics */}
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-full bg-impilo-50 flex items-center justify-center border-2 border-impilo-200">
            <User className="w-5 h-5 text-impilo-500" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-semibold text-gray-900">
                {patient.name}
              </h2>
              <span className="text-[10px] font-mono border rounded px-1.5 py-0.5 text-gray-500 border-gray-200">
                {patient.mrn}
              </span>
              <span
                className={`text-[10px] border rounded px-1.5 py-0.5 ${genderBadgeClasses}`}
              >
                {genderLabel} &bull; {age}y
              </span>
            </div>
            <div className="flex items-center gap-3 text-xs text-gray-500 mt-0.5">
              <span className="flex items-center gap-1">
                <Calendar className="w-3 h-3" />
                DOB: {formatDate(patient.dateOfBirth)}
              </span>
              <span className="flex items-center gap-1">
                <MapPin className="w-3 h-3" />
                {patient.ward} &bull; {patient.bed}
              </span>
              {primaryDiagnosis && (
                <span className="flex items-center gap-1">
                  <Stethoscope className="w-3 h-3 text-impilo-500" />
                  {primaryDiagnosis.name}
                  <span className="text-[9px] border rounded px-1 py-0.5 font-mono text-gray-400 border-gray-200">
                    {primaryDiagnosis.icdCode}
                  </span>
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Right: encounter type + LOS */}
        <div className="text-right">
          <span
            className={`inline-block text-[10px] font-medium rounded px-2 py-0.5 ${
              encounter.status === "active"
                ? "bg-impilo-500 text-white"
                : "bg-gray-100 text-gray-600"
            }`}
          >
            {encounter.type.toUpperCase()}
          </span>
          <div className="text-[10px] text-gray-500 mt-0.5">
            LOS: {los} days
          </div>
        </div>
      </div>
    </div>
  );
}
