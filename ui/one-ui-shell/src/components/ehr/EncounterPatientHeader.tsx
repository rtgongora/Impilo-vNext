"use client";

/**
 * EncounterPatientHeader -- Compact patient header with demographics, MRN,
 * age, gender, location badge, encounter metadata (type, duration, provider).
 *
 * Pulls data from usePatient and useEncounter hooks via VITO / encounter context.
 */

import { useMemo } from "react";
import { User, MapPin, Calendar, Loader2, AlertTriangle } from "lucide-react";
import { usePrivacyDisplayStore } from "@/hooks/usePrivacyDisplayStore";
import { maskName, maskDob, maskId, safeAge, genderChar } from "@/lib/pii-mask";
import { usePatient } from "@/hooks/queries/usePatients";
import { useEncounter } from "@/hooks/queries/useEncounters";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function daysBetween(a: string, b: Date): number {
  return Math.floor(
    (b.getTime() - new Date(a).getTime()) / (1000 * 60 * 60 * 24),
  );
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

interface EncounterPatientHeaderProps {
  patientId: string;
  encounterId: string;
}

export function EncounterPatientHeader({ patientId, encounterId }: EncounterPatientHeaderProps) {
  const { data: patientData, isLoading: patientLoading, error: patientError } = usePatient(patientId);
  const { data: encounterData, isLoading: encounterLoading, error: encounterError } = useEncounter(encounterId);
  const privacyLevel = usePrivacyDisplayStore((s) => s.level);

  const isLoading = patientLoading || encounterLoading;
  const hasError = patientError || encounterError;

  const patient = useMemo(() => {
    if (!patientData?.data) return null;
    const p = patientData.data;
    return {
      name: p.attributes.displayName || "",
      mrn: p.attributes.cpid || p.id,
      gender: (p.attributes.gender || "other") as "male" | "female" | "other",
      dateOfBirth: p.attributes.dateOfBirth || "",
      ward: "",
      bed: "",
    };
  }, [patientData]);

  const encounter = useMemo(() => {
    if (!encounterData?.data) return null;
    const e = encounterData.data;
    return {
      type: (e.attributes.encounterType || "outpatient") as "inpatient" | "outpatient" | "emergency",
      status: (e.attributes.status || "active") as "active" | "completed",
      admissionDate: e.attributes.startedAt ? e.attributes.startedAt.substring(0, 10) : "",
    };
  }, [encounterData]);

  if (isLoading) {
    return (
      <div className="bg-card border-b border-border px-4 py-2">
        <div className="flex items-center gap-2">
          <Loader2 className="h-4 w-4 animate-spin text-primary" />
          <span className="text-sm text-muted-foreground">Loading patient...</span>
        </div>
      </div>
    );
  }

  if (hasError || !patient) {
    return (
      <div className="bg-card border-b border-border px-4 py-2">
        <div className="flex items-center gap-2 text-red-600">
          <AlertTriangle className="h-4 w-4" />
          <span className="text-sm">Failed to load patient information.</span>
        </div>
      </div>
    );
  }

  const age = safeAge(patient.dateOfBirth);
  const los = encounter?.admissionDate ? daysBetween(encounter.admissionDate, new Date()) : 0;

  const genderBadgeClasses =
    patient.gender === "female"
      ? "bg-pink-50 text-pink-600 border-pink-200"
      : patient.gender === "male"
        ? "bg-primary-soft text-primary border-primary/25"
        : "bg-warning-soft text-purple-600 border-warning/35";

  const genderLabel = genderChar(patient.gender);

  return (
    <div className="bg-card border-b border-border px-4 py-2">
      <div className="flex items-center justify-between">
        {/* Left: avatar + demographics */}
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-full bg-primary-soft flex items-center justify-center border-2 border-primary/25">
            <User className="w-5 h-5 text-primary" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-semibold text-foreground">
                {maskName(patient.name, privacyLevel)}
              </h2>
              <span className="text-[10px] font-mono border rounded px-1.5 py-0.5 text-muted-foreground border-border">
                {maskId(patient.mrn, privacyLevel)}
              </span>
              <span
                className={`text-[10px] border rounded px-1.5 py-0.5 ${genderBadgeClasses}`}
              >
                {genderLabel} &bull; {age != null ? `${age}y` : "\u2014"}
              </span>
            </div>
            <div className="flex items-center gap-3 text-xs text-muted-foreground mt-0.5">
              <span className="flex items-center gap-1">
                <Calendar className="w-3 h-3" />
                {maskDob(patient.dateOfBirth, privacyLevel)}
              </span>
              {(patient.ward || patient.bed) && (
                <span className="flex items-center gap-1">
                  <MapPin className="w-3 h-3" />
                  {patient.ward}{patient.ward && patient.bed ? " \u2022 " : ""}{patient.bed}
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Right: encounter type + LOS */}
        {encounter && (
          <div className="text-right">
            <span
              className={`inline-block text-[10px] font-medium rounded px-2 py-0.5 ${
                encounter.status === "active"
                  ? "bg-primary text-white"
                  : "bg-neutral-100 text-muted-foreground"
              }`}
            >
              {encounter.type.toUpperCase()}
            </span>
            <div className="text-[10px] text-muted-foreground mt-0.5">
              LOS: {los} days
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
