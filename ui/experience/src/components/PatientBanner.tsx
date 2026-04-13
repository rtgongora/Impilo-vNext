"use client";

/**
 * PatientBanner — Persistent patient-context strip in the EHR workspace.
 *
 * Lovable-aligned: shows patient identity, demographics, allergy alert,
 * active conditions count, active encounter status, and key context at a
 * glance. Collapsible for more detail. Uses real API data from usePatient,
 * useEncounters, useAllergies, and conditions queries.
 *
 * Renders only when a patientId is present in the URL params.
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
  User,
  Calendar,
  Activity,
  AlertTriangle,
  ChevronDown,
  ChevronUp,
  Clock,
  FileText,
  MapPin,
  Shield,
  ShieldAlert,
  Stethoscope,
  Video,
} from "lucide-react";
import { usePatient } from "@/hooks/queries/usePatients";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useAllergies, type AllergyResource } from "@/hooks/queries/useAllergies";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useShiftStore } from "@/hooks/useShiftStore";

interface GenericResource {
  id: string;
  type: string;
  attributes: Record<string, unknown>;
}

const SEVERITY_COLORS: Record<string, string> = {
  MILD: "bg-yellow-50 text-yellow-700 border-yellow-200",
  MODERATE: "bg-orange-50 text-orange-700 border-orange-200",
  SEVERE: "bg-red-50 text-red-700 border-red-200",
};

export function PatientBanner() {
  const params = useParams();
  const patientId = params?.patientId as string | undefined;
  const [expanded, setExpanded] = useState(() => {
    if (typeof window === "undefined") return false;
    return sessionStorage.getItem("exp:patient-banner-expanded") === "true";
  });

  const { data: patientData } = usePatient(patientId ?? "");
  const { data: encountersData } = useEncounters(patientId ?? "");
  const { data: allergiesData } = useAllergies(patientId ?? "");
  const { data: conditionsData } = useQuery<ApiResponse<GenericResource[]>>({
    queryKey: ["conditions", { patientId }],
    queryFn: () =>
      apiClient.get<ApiResponse<GenericResource[]>>(
        `/internal/v1/conditions?patient_id=${patientId}`
      ),
    enabled: !!patientId,
  });
  const { facility } = useFacilityStore();
  const { shift } = useShiftStore();

  if (!patientId) return null;

  const patient = patientData?.data;
  if (!patient) return null;

  const encounters = encountersData?.data ?? [];
  const allergies: AllergyResource[] = allergiesData?.data ?? [];
  const activeAllergies = allergies.filter(
    (a) => a.attributes.status === "ACTIVE"
  );
  const severeAllergies = activeAllergies.filter(
    (a) => a.attributes.severity === "SEVERE"
  );
  const hasAllergies = activeAllergies.length > 0;
  const hasSevere = severeAllergies.length > 0;

  const conditions: GenericResource[] = conditionsData?.data ?? [];
  const activeConditions = conditions.filter(
    (c) =>
      c.attributes.clinical_status === "ACTIVE" ||
      c.attributes.clinicalStatus === "ACTIVE"
  );

  const activeEncounter = encounters.find(
    (e) =>
      e.attributes.status === "IN_PROGRESS" || e.attributes.status === "ACTIVE"
  );

  const attrs = patient.attributes;
  const age = attrs.dateOfBirth
    ? Math.floor(
        (Date.now() - new Date(attrs.dateOfBirth).getTime()) /
          (365.25 * 24 * 60 * 60 * 1000)
      )
    : null;
  const genderBadge =
    attrs.gender === "female"
      ? "bg-pink-50 text-pink-600 border-pink-200"
      : attrs.gender === "male"
        ? "bg-impilo-50 text-impilo-500 border-impilo-200"
        : "bg-purple-50 text-purple-600 border-purple-200";
  const genderChar =
    attrs.gender === "female" ? "F" : attrs.gender === "male" ? "M" : "O";

  function toggleExpanded(next: boolean | ((current: boolean) => boolean)) {
    const resolved = typeof next === "function" ? next(expanded) : next;
    setExpanded(resolved);
    if (typeof window !== "undefined") {
      sessionStorage.setItem("exp:patient-banner-expanded", String(resolved));
    }
  }

  return (
    <div className="bg-white border-b border-gray-200">
      {/* Compact Banner — Always Visible */}
      <div className="px-4 py-2">
        <div className="flex items-center justify-between gap-4">
          {/* Patient Identity */}
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-impilo-50 flex items-center justify-center border-2 border-impilo-200">
              <User className="w-5 h-5 text-impilo-500" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <Link
                  href={`/ehr/${patientId}`}
                  className="text-base font-semibold text-gray-900 hover:text-impilo-600 transition-colors"
                >
                  {attrs.displayName}
                </Link>
                <span className="px-1.5 py-0.5 text-xs font-mono bg-gray-100 text-gray-600 rounded border border-gray-200">
                  {attrs.cpid}
                </span>
                <span
                  className={`px-1.5 py-0.5 text-xs rounded border ${genderBadge}`}
                >
                  {genderChar}
                  {age != null ? ` • ${age}y` : ""}
                </span>
              </div>
              <div className="flex items-center gap-3 text-xs text-gray-500 mt-0.5">
                {attrs.dateOfBirth && (
                  <span className="flex items-center gap-1">
                    <Calendar className="w-3 h-3" />
                    DOB: {attrs.dateOfBirth}
                  </span>
                )}
                {facility && (
                  <span className="flex items-center gap-1">
                    <MapPin className="w-3 h-3" />
                    {facility.name}
                  </span>
                )}
              </div>
            </div>
          </div>

          {/* Allergy Alert — Lovable-aligned compact display */}
          <div className="flex items-center gap-2">
            {hasAllergies ? (
              <button
                onClick={() => toggleExpanded(true)}
                className={`flex items-center gap-2 px-3 py-1.5 rounded-lg border cursor-pointer transition-colors hover:opacity-80 ${
                  hasSevere
                    ? "bg-red-50 border-red-200"
                    : "bg-yellow-50 border-yellow-200"
                }`}
              >
                {hasSevere ? (
                  <ShieldAlert className="w-4 h-4 text-red-600" />
                ) : (
                  <AlertTriangle className="w-4 h-4 text-yellow-600" />
                )}
                <span className="text-sm">
                  <span
                    className={`font-medium ${hasSevere ? "text-red-700" : "text-yellow-700"}`}
                  >
                    Allergies:
                  </span>
                  <span className="ml-1 text-gray-700">
                    {activeAllergies
                      .slice(0, 3)
                      .map((a) => a.attributes.allergen)
                      .join(", ")}
                    {activeAllergies.length > 3 &&
                      ` +${activeAllergies.length - 3} more`}
                  </span>
                </span>
              </button>
            ) : (
              <div className="flex items-center gap-2 px-3 py-1.5 bg-green-50 border border-green-200 rounded-lg">
                <Shield className="w-4 h-4 text-green-600" />
                <span className="text-sm font-medium text-green-700">NKDA</span>
              </div>
            )}

            {/* Active Conditions Count */}
            {activeConditions.length > 0 && (
              <Link
                href={`/ehr/${patientId}/conditions`}
                className="flex items-center gap-1.5 px-2.5 py-1.5 bg-orange-50 border border-orange-200 rounded-lg hover:bg-orange-100 transition-colors"
              >
                <Stethoscope className="w-3.5 h-3.5 text-orange-600" />
                <span className="text-xs font-medium text-orange-700">
                  {activeConditions.length} Active Condition
                  {activeConditions.length !== 1 ? "s" : ""}
                </span>
              </Link>
            )}
          </div>

          {/* Active Encounter Status */}
          <div className="flex items-center gap-3">
            {activeEncounter ? (
              <Link
                href={`/ehr/${patientId}/encounter/${activeEncounter.id}`}
                className="flex items-center gap-2 px-3 py-1.5 bg-green-50 border border-green-200 rounded-lg hover:bg-green-100 transition-colors"
              >
                <Activity className="w-4 h-4 text-green-600" />
                <div>
                  <span className="text-xs font-medium text-green-700 block">
                    Active: {activeEncounter.attributes.encounterType}
                  </span>
                  <span className="text-[10px] text-green-600">
                    Since{" "}
                    {new Date(
                      activeEncounter.attributes.startedAt
                    ).toLocaleTimeString()}
                  </span>
                </div>
              </Link>
            ) : (
              <span className="px-3 py-1.5 bg-gray-50 border border-gray-200 text-xs text-gray-500 rounded-lg">
                No active encounter
              </span>
            )}

            {shift && (
              <span className="px-2 py-1 bg-amber-50 text-amber-700 border border-amber-200 text-xs rounded-lg flex items-center gap-1">
                <Clock className="w-3 h-3" />
                Shift Active
              </span>
            )}

            <div className="hidden xl:flex items-center gap-2">
              <Link
                href={`/ehr/${patientId}/summary`}
                className="px-2.5 py-1.5 text-xs font-medium text-gray-600 bg-gray-50 border border-gray-200 rounded-lg hover:bg-gray-100 transition-colors"
              >
                Summary
              </Link>
              <Link
                href={`/ehr/${patientId}/consults`}
                className="px-2.5 py-1.5 text-xs font-medium text-indigo-700 bg-indigo-50 border border-indigo-200 rounded-lg hover:bg-indigo-100 transition-colors flex items-center gap-1"
              >
                <Video className="w-3 h-3" />
                Consults
              </Link>
              <Link
                href={`/ehr/${patientId}/notes`}
                className="px-2.5 py-1.5 text-xs font-medium text-gray-600 bg-gray-50 border border-gray-200 rounded-lg hover:bg-gray-100 transition-colors flex items-center gap-1"
              >
                <FileText className="w-3 h-3" />
                Notes
              </Link>
            </div>

            {/* Expand/Collapse */}
            <button
              onClick={() => toggleExpanded((v) => !v)}
              className="px-2 py-1.5 text-xs text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded transition-colors flex items-center gap-1"
            >
              {expanded ? (
                <>
                  <ChevronUp className="w-3.5 h-3.5" /> Less
                </>
              ) : (
                <>
                  <ChevronDown className="w-3.5 h-3.5" /> More
                </>
              )}
            </button>
          </div>
        </div>
      </div>

      {/* Expanded Section */}
      {expanded && (
        <div className="border-t border-gray-100 px-4 py-3">
          <div className="grid grid-cols-3 gap-6">
            {/* Allergies Detail */}
            <div className="space-y-2">
              <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wide flex items-center gap-1.5">
                <ShieldAlert className="w-3.5 h-3.5" /> Allergies (
                {activeAllergies.length})
              </h4>
              {activeAllergies.length === 0 ? (
                <div className="flex items-center gap-2 p-2 bg-green-50 rounded-lg">
                  <Shield className="w-4 h-4 text-green-600" />
                  <span className="text-sm text-green-700">
                    No Known Drug Allergies (NKDA)
                  </span>
                </div>
              ) : (
                <div className="space-y-1.5">
                  {activeAllergies.map((allergy) => {
                    const a = allergy.attributes;
                    const severityStyle =
                      SEVERITY_COLORS[a.severity] ??
                      "bg-gray-50 text-gray-700 border-gray-200";
                    return (
                      <div
                        key={allergy.id}
                        className={`px-3 py-2 rounded-lg border ${severityStyle}`}
                      >
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
                            <span className="text-sm font-medium">
                              {a.allergen}
                            </span>
                            <span className="text-xs opacity-75 capitalize">
                              {a.allergenType}
                            </span>
                          </div>
                          <span className="text-xs opacity-75 capitalize">
                            {a.severity}
                          </span>
                        </div>
                        {a.reaction && (
                          <p className="text-xs mt-1 ml-5.5 opacity-80">
                            Reaction: {a.reaction}
                          </p>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* Active Conditions */}
            <div className="space-y-2">
              <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wide flex items-center gap-1.5">
                <Stethoscope className="w-3.5 h-3.5" /> Active Conditions (
                {activeConditions.length})
              </h4>
              {activeConditions.length === 0 ? (
                <p className="text-sm text-gray-400 italic">
                  No active conditions
                </p>
              ) : (
                <div className="space-y-1.5">
                  {activeConditions.slice(0, 5).map((cond) => {
                    const ca = cond.attributes;
                    const severityStyle =
                      ca.severity === "SEVERE"
                        ? "border-red-200 bg-red-50"
                        : ca.severity === "MODERATE"
                          ? "border-orange-200 bg-orange-50"
                          : "border-gray-200 bg-gray-50";
                    return (
                      <div
                        key={cond.id}
                        className={`px-3 py-2 rounded-lg border ${severityStyle}`}
                      >
                        <div className="flex items-center justify-between">
                          <span className="text-sm font-medium text-gray-900">
                            {String(ca.conditionName ?? ca.condition_name ?? "")}
                          </span>
                          <span className="text-xs text-gray-500 capitalize">
                            {String(ca.severity ?? "")}
                          </span>
                        </div>
                        {typeof ca.icdCode === "string" && (
                          <span className="text-xs text-gray-400 font-mono">
                            ICD: {String(ca.icdCode ?? ca.icd_code ?? "")}
                          </span>
                        )}
                      </div>
                    );
                  })}
                  {activeConditions.length > 5 && (
                    <Link
                      href={`/ehr/${patientId}/conditions`}
                      className="text-xs text-impilo-500 hover:text-impilo-700"
                    >
                      +{activeConditions.length - 5} more conditions
                    </Link>
                  )}
                </div>
              )}
            </div>

            {/* Encounter & Recent History */}
            <div className="space-y-2">
              <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wide flex items-center gap-1.5">
                <Activity className="w-3.5 h-3.5" /> Encounter
              </h4>
              {activeEncounter ? (
                <div className="space-y-1.5 text-sm">
                  <div className="flex justify-between">
                    <span className="text-gray-500">Type:</span>
                    <span className="font-medium text-gray-900">
                      {activeEncounter.attributes.encounterType}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-500">Status:</span>
                    <span className="px-2 py-0.5 text-xs rounded-full bg-green-100 text-green-700">
                      {activeEncounter.attributes.status}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-500">Started:</span>
                    <span className="font-medium text-gray-900">
                      {new Date(
                        activeEncounter.attributes.startedAt
                      ).toLocaleString()}
                    </span>
                  </div>
                </div>
              ) : (
                <p className="text-sm text-gray-400 italic">
                  No active encounter
                </p>
              )}

              {encounters.length > 0 && (
                <div className="mt-3 pt-2 border-t border-gray-100">
                  <p className="text-xs text-gray-400 mb-1.5">Recent:</p>
                  {encounters.slice(0, 3).map((enc) => (
                    <Link
                      key={enc.id}
                      href={`/ehr/${patientId}/encounter/${enc.id}`}
                      className="flex items-center justify-between text-sm hover:bg-gray-50 rounded px-1 -mx-1 py-0.5 transition-colors"
                    >
                      <span className="text-gray-700">
                        {enc.attributes.encounterType}
                      </span>
                      <span
                        className={`px-1.5 py-0.5 text-xs rounded-full ${
                          enc.attributes.status === "IN_PROGRESS" ||
                          enc.attributes.status === "ACTIVE"
                            ? "bg-green-100 text-green-700"
                            : "bg-gray-100 text-gray-600"
                        }`}
                      >
                        {enc.attributes.status}
                      </span>
                    </Link>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
