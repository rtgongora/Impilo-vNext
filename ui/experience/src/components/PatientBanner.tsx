"use client";

/**
 * PatientBanner — Persistent patient-context strip in the EHR workspace.
 *
 * Lovable-aligned: shows patient identity, demographics, active encounter
 * status, allergies alert, and key context at a glance. Collapsible for
 * more detail. Uses real API data from usePatient and useEncounters hooks.
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
  MapPin,
  ShieldAlert,
} from "lucide-react";
import { usePatient } from "@/hooks/queries/usePatients";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useShiftStore } from "@/hooks/useShiftStore";

export function PatientBanner() {
  const params = useParams();
  const patientId = params?.patientId as string | undefined;
  const [expanded, setExpanded] = useState(false);

  const { data: patientData } = usePatient(patientId ?? "");
  const { data: encountersData } = useEncounters(patientId ?? "");
  const { facility } = useFacilityStore();
  const { shift } = useShiftStore();

  if (!patientId) return null;

  const patient = patientData?.data;
  if (!patient) return null;

  const encounters = encountersData?.data ?? [];
  const activeEncounter = encounters.find(
    (e) => e.attributes.status === "IN_PROGRESS" || e.attributes.status === "ACTIVE"
  );

  const attrs = patient.attributes;
  const age = attrs.dateOfBirth
    ? Math.floor(
        (Date.now() - new Date(attrs.dateOfBirth).getTime()) / (365.25 * 24 * 60 * 60 * 1000)
      )
    : null;
  const genderBadge =
    attrs.gender === "female"
      ? "bg-pink-50 text-pink-600 border-pink-200"
      : attrs.gender === "male"
        ? "bg-blue-50 text-blue-600 border-blue-200"
        : "bg-purple-50 text-purple-600 border-purple-200";
  const genderChar = attrs.gender === "female" ? "F" : attrs.gender === "male" ? "M" : "O";

  return (
    <div className="bg-white border-b border-gray-200">
      {/* Compact Banner — Always Visible */}
      <div className="px-4 py-2">
        <div className="flex items-center justify-between gap-4">
          {/* Patient Identity */}
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-blue-50 flex items-center justify-center border-2 border-blue-200">
              <User className="w-5 h-5 text-blue-600" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <Link
                  href={`/ehr/${patientId}`}
                  className="text-base font-semibold text-gray-900 hover:text-blue-700 transition-colors"
                >
                  {attrs.displayName}
                </Link>
                <span className="px-1.5 py-0.5 text-xs font-mono bg-gray-100 text-gray-600 rounded border border-gray-200">
                  {attrs.cpid}
                </span>
                <span className={`px-1.5 py-0.5 text-xs rounded border ${genderBadge}`}>
                  {genderChar}{age != null ? ` • ${age}y` : ""}
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
                    Since {new Date(activeEncounter.attributes.startedAt).toLocaleTimeString()}
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

            {/* Expand/Collapse */}
            <button
              onClick={() => setExpanded((v) => !v)}
              className="px-2 py-1.5 text-xs text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded transition-colors flex items-center gap-1"
            >
              {expanded ? (
                <><ChevronUp className="w-3.5 h-3.5" /> Less</>
              ) : (
                <><ChevronDown className="w-3.5 h-3.5" /> More</>
              )}
            </button>
          </div>
        </div>
      </div>

      {/* Expanded Section */}
      {expanded && (
        <div className="border-t border-gray-100 px-4 py-3">
          <div className="grid grid-cols-3 gap-6">
            {/* Demographics */}
            <div className="space-y-2">
              <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wide flex items-center gap-1.5">
                <User className="w-3.5 h-3.5" /> Demographics
              </h4>
              <div className="space-y-1.5 text-sm">
                <div className="flex justify-between">
                  <span className="text-gray-500">Gender:</span>
                  <span className="font-medium text-gray-900 capitalize">{attrs.gender}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">Date of Birth:</span>
                  <span className="font-medium text-gray-900">{attrs.dateOfBirth}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-500">CPID:</span>
                  <span className="font-medium font-mono text-gray-900">{attrs.cpid}</span>
                </div>
                {facility && (
                  <div className="flex justify-between">
                    <span className="text-gray-500">Facility:</span>
                    <span className="font-medium text-gray-900">{facility.name}</span>
                  </div>
                )}
              </div>
            </div>

            {/* Active Encounter Details */}
            <div className="space-y-2">
              <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wide flex items-center gap-1.5">
                <Activity className="w-3.5 h-3.5" /> Encounter
              </h4>
              {activeEncounter ? (
                <div className="space-y-1.5 text-sm">
                  <div className="flex justify-between">
                    <span className="text-gray-500">Type:</span>
                    <span className="font-medium text-gray-900">{activeEncounter.attributes.encounterType}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-500">Status:</span>
                    <span className="px-2 py-0.5 text-xs rounded-full bg-green-100 text-green-700">{activeEncounter.attributes.status}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-500">Started:</span>
                    <span className="font-medium text-gray-900">{new Date(activeEncounter.attributes.startedAt).toLocaleString()}</span>
                  </div>
                </div>
              ) : (
                <p className="text-sm text-gray-400 italic">No active encounter</p>
              )}
            </div>

            {/* Recent Encounters */}
            <div className="space-y-2">
              <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wide flex items-center gap-1.5">
                <Clock className="w-3.5 h-3.5" /> Recent Encounters
              </h4>
              {encounters.length > 0 ? (
                <div className="space-y-1.5">
                  {encounters.slice(0, 3).map((enc) => (
                    <Link
                      key={enc.id}
                      href={`/ehr/${patientId}/encounter/${enc.id}`}
                      className="flex items-center justify-between text-sm hover:bg-gray-50 rounded px-1 -mx-1 py-0.5 transition-colors"
                    >
                      <span className="text-gray-700">{enc.attributes.encounterType}</span>
                      <span className={`px-1.5 py-0.5 text-xs rounded-full ${
                        enc.attributes.status === "IN_PROGRESS" || enc.attributes.status === "ACTIVE"
                          ? "bg-green-100 text-green-700"
                          : "bg-gray-100 text-gray-600"
                      }`}>{enc.attributes.status}</span>
                    </Link>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-gray-400 italic">No encounters</p>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
