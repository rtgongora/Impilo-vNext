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
  Siren,
} from "lucide-react";
import { usePatient } from "@/hooks/queries/usePatients";
import { usePatientSummary } from "@/hooks/queries/useSummary";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useAllergies, type AllergyResource } from "@/hooks/queries/useAllergies";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { usePrivacyDisplayStore } from "@/hooks/usePrivacyDisplayStore";
import { maskName, maskDob, safeAge, displayCpid, genderChar } from "@/lib/pii-mask";

interface GenericResource {
  id: string;
  type: string;
  attributes: Record<string, unknown>;
}

const SEVERITY_COLORS: Record<string, string> = {
  MILD: "bg-yellow-50 text-yellow-700 border-yellow-200",
  MODERATE: "bg-orange-50 text-orange-700 border-orange-200",
  SEVERE: "bg-danger-soft text-danger border-danger/28",
};

function consentChipClass(sev: string | undefined): string {
  switch (sev) {
    case "CRITICAL":
      return "bg-danger-soft text-red-900 border-danger/28";
    case "HIGH":
      return "bg-warning-soft text-warning-foreground border-warning/35";
    case "MODERATE":
      return "bg-orange-50 text-orange-800 border-orange-200";
    default:
      return "bg-background text-foreground border-border";
  }
}

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
  const { data: patientSummaryRes } = usePatientSummary(patientId);
  const { facility } = useFacilityStore();
  const { shift } = useShiftStore();
  const privacyLevel = usePrivacyDisplayStore((s) => s.level);

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

  const highBannerConsentFlags = (patientSummaryRes?.data?.consentSummary?.criticalFlags ?? []).filter(
    (f) => f.severity === "CRITICAL" || f.severity === "HIGH",
  );
  const bannerConsentFlags = highBannerConsentFlags.slice(0, 8);
  const moreConsentFlags = Math.max(0, highBannerConsentFlags.length - 8);

  const attrs = patient.attributes;
  const age = safeAge(attrs.dateOfBirth);
  const genderBadge =
    attrs.gender === "female"
      ? "bg-pink-50 text-pink-600 border-pink-200"
      : attrs.gender === "male"
        ? "bg-primary-soft text-primary border-primary/25"
        : "bg-warning-soft text-purple-600 border-warning/35";
  const gChar = genderChar(attrs.gender);

  function toggleExpanded(next: boolean | ((current: boolean) => boolean)) {
    const resolved = typeof next === "function" ? next(expanded) : next;
    setExpanded(resolved);
    if (typeof window !== "undefined") {
      sessionStorage.setItem("exp:patient-banner-expanded", String(resolved));
    }
  }

  return (
    <div className="bg-card border-b border-border">
      {/* Compact Banner — Always Visible */}
      <div className="px-4 py-2">
        <div className="flex items-center justify-between gap-4">
          {/* Patient Identity */}
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-primary-soft flex items-center justify-center border-2 border-primary/25">
              <User className="w-5 h-5 text-primary" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <Link
                  href={`/ehr/${patientId}`}
                  className="text-base font-semibold text-foreground hover:text-primary transition-colors"
                >
                  {maskName(attrs.displayName, privacyLevel)}
                </Link>
                <span className="px-1.5 py-0.5 text-xs font-mono bg-neutral-100 text-muted-foreground rounded border border-border">
                  {displayCpid(attrs.cpid)}
                </span>
                <span
                  className={`px-1.5 py-0.5 text-xs rounded border ${genderBadge}`}
                >
                  {gChar}
                  {age != null ? ` • ${age}y` : ""}
                </span>
              </div>
              <div className="flex items-center gap-3 text-xs text-muted-foreground mt-0.5">
                {attrs.dateOfBirth && (
                  <span className="flex items-center gap-1">
                    <Calendar className="w-3 h-3" />
                    {maskDob(attrs.dateOfBirth, privacyLevel)}
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
                    ? "bg-danger-soft border-danger/28"
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
                    className={`font-medium ${hasSevere ? "text-danger" : "text-yellow-700"}`}
                  >
                    Allergies:
                  </span>
                  <span className="ml-1 text-foreground">
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
              <span className="px-3 py-1.5 bg-background border border-border text-xs text-muted-foreground rounded-lg">
                No active encounter
              </span>
            )}

            {shift && (
              <span className="px-2 py-1 bg-warning-soft text-warning-foreground border border-warning/35 text-xs rounded-lg flex items-center gap-1">
                <Clock className="w-3 h-3" />
                Shift Active
              </span>
            )}

            <div className="hidden xl:flex items-center gap-2">
              <Link
                href={`/ehr/${patientId}/emergency`}
                className="inline-flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium text-red-800 bg-danger-soft border border-danger/28 rounded-lg hover:bg-red-100 transition-colors"
              >
                <Siren className="h-3.5 w-3.5" />
                Emergency
              </Link>
              <Link
                href={`/ehr/${patientId}/summary`}
                className="px-2.5 py-1.5 text-xs font-medium text-muted-foreground bg-background border border-border rounded-lg hover:bg-neutral-100 transition-colors"
              >
                Summary
              </Link>
              <Link
                href={`/ehr/${patientId}/consults`}
                className="px-2.5 py-1.5 text-xs font-medium text-primary-hover bg-info-soft border border-info/25 rounded-lg hover:bg-indigo-100 transition-colors flex items-center gap-1"
              >
                <Video className="w-3 h-3" />
                Consults
              </Link>
              <Link
                href={`/ehr/${patientId}/notes`}
                className="px-2.5 py-1.5 text-xs font-medium text-muted-foreground bg-background border border-border rounded-lg hover:bg-neutral-100 transition-colors flex items-center gap-1"
              >
                <FileText className="w-3 h-3" />
                Notes
              </Link>
            </div>

            {/* Expand/Collapse */}
            <button
              onClick={() => toggleExpanded((v) => !v)}
              className="px-2 py-1.5 text-xs text-muted-foreground hover:text-foreground hover:bg-neutral-100 rounded transition-colors flex items-center gap-1"
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

        {bannerConsentFlags.length > 0 && (
          <div className="mt-2 flex flex-wrap items-center gap-1.5 border-t border-border pt-2">
            <span className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
              Consent / directives
            </span>
            {bannerConsentFlags.map((f) => (
              <Link
                key={`${f.code ?? "flag"}-${f.id ?? f.label}`}
                href={`/ehr/${patientId}/summary#consent-preferences`}
                className={`inline-flex max-w-[14rem] truncate rounded border px-2 py-0.5 text-xs font-medium transition-opacity hover:opacity-90 ${consentChipClass(f.severity)}`}
                title={[
                  f.label,
                  f.severity,
                  f.source,
                  f.effectiveDate ? `from ${f.effectiveDate}` : "",
                  f.expiryDate ? `until ${f.expiryDate}` : "",
                ]
                  .filter(Boolean)
                  .join(" · ")}
              >
                {f.label ?? f.code ?? "Flag"}
              </Link>
            ))}
            {moreConsentFlags > 0 && (
              <Link
                href={`/ehr/${patientId}/summary#consent-preferences`}
                className="text-xs font-medium text-primary hover:text-impilo-800"
              >
                +{moreConsentFlags} more
              </Link>
            )}
          </div>
        )}
      </div>

      {/* Expanded Section */}
      {expanded && (
        <div className="border-t border-border px-4 py-3">
          <div className="grid grid-cols-3 gap-6">
            {/* Allergies Detail */}
            <div className="space-y-2">
              <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wide flex items-center gap-1.5">
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
                      "bg-background text-foreground border-border";
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
              <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wide flex items-center gap-1.5">
                <Stethoscope className="w-3.5 h-3.5" /> Active Conditions (
                {activeConditions.length})
              </h4>
              {activeConditions.length === 0 ? (
                <p className="text-sm text-muted-foreground italic">
                  No active conditions
                </p>
              ) : (
                <div className="space-y-1.5">
                  {activeConditions.slice(0, 5).map((cond) => {
                    const ca = cond.attributes;
                    const severityStyle =
                      ca.severity === "SEVERE"
                        ? "border-danger/28 bg-danger-soft"
                        : ca.severity === "MODERATE"
                          ? "border-orange-200 bg-orange-50"
                          : "border-border bg-background";
                    return (
                      <div
                        key={cond.id}
                        className={`px-3 py-2 rounded-lg border ${severityStyle}`}
                      >
                        <div className="flex items-center justify-between">
                          <span className="text-sm font-medium text-foreground">
                            {String(ca.conditionName ?? ca.condition_name ?? "")}
                          </span>
                          <span className="text-xs text-muted-foreground capitalize">
                            {String(ca.severity ?? "")}
                          </span>
                        </div>
                        {typeof ca.icdCode === "string" && (
                          <span className="text-xs text-muted-foreground font-mono">
                            ICD: {String(ca.icdCode ?? ca.icd_code ?? "")}
                          </span>
                        )}
                      </div>
                    );
                  })}
                  {activeConditions.length > 5 && (
                    <Link
                      href={`/ehr/${patientId}/conditions`}
                      className="text-xs text-primary hover:text-primary-hover"
                    >
                      +{activeConditions.length - 5} more conditions
                    </Link>
                  )}
                </div>
              )}
            </div>

            {/* Encounter & Recent History */}
            <div className="space-y-2">
              <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wide flex items-center gap-1.5">
                <Activity className="w-3.5 h-3.5" /> Encounter
              </h4>
              {activeEncounter ? (
                <div className="space-y-1.5 text-sm">
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">Type:</span>
                    <span className="font-medium text-foreground">
                      {activeEncounter.attributes.encounterType}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">Status:</span>
                    <span className="px-2 py-0.5 text-xs rounded-full bg-green-100 text-green-700">
                      {activeEncounter.attributes.status}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">Started:</span>
                    <span className="font-medium text-foreground">
                      {new Date(
                        activeEncounter.attributes.startedAt
                      ).toLocaleString()}
                    </span>
                  </div>
                </div>
              ) : (
                <p className="text-sm text-muted-foreground italic">
                  No active encounter
                </p>
              )}

              {encounters.length > 0 && (
                <div className="mt-3 pt-2 border-t border-border">
                  <p className="text-xs text-muted-foreground mb-1.5">Recent:</p>
                  {encounters.slice(0, 3).map((enc) => (
                    <Link
                      key={enc.id}
                      href={`/ehr/${patientId}/encounter/${enc.id}`}
                      className="flex items-center justify-between text-sm hover:bg-background rounded px-1 -mx-1 py-0.5 transition-colors"
                    >
                      <span className="text-foreground">
                        {enc.attributes.encounterType}
                      </span>
                      <span
                        className={`px-1.5 py-0.5 text-xs rounded-full ${
                          enc.attributes.status === "IN_PROGRESS" ||
                          enc.attributes.status === "ACTIVE"
                            ? "bg-green-100 text-green-700"
                            : "bg-neutral-100 text-muted-foreground"
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
