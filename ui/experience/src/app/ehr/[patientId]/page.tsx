"use client";

/**
 * Patient Chart — Overview of patient demographics and navigation to sub-pages.
 * Route: /ehr/[patientId] | pageTitle: "Patient Chart"
 */

import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  User,
  Loader2,
  Activity,
  FileText,
  Pill,
  AlertCircle,
  ClipboardList,
  TestTube2,
  Syringe,
  Clock,
  ArrowLeft,
} from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { usePatient } from "@/hooks/queries/usePatients";
import { useEncounters, useCreateEncounter } from "@/hooks/queries/useEncounters";
import { useFacilityStore } from "@/hooks/useFacilityStore";

const CHART_SECTIONS = [
  { label: "Vitals", href: "vitals", icon: Activity, color: "bg-red-100 text-red-600" },
  { label: "Conditions", href: "conditions", icon: AlertCircle, color: "bg-orange-100 text-orange-600" },
  { label: "Medications", href: "medications", icon: Pill, color: "bg-green-100 text-green-600" },
  { label: "Allergies", href: "allergies", icon: AlertCircle, color: "bg-yellow-100 text-yellow-600" },
  { label: "Orders", href: "orders", icon: ClipboardList, color: "bg-blue-100 text-blue-600" },
  { label: "Results", href: "results", icon: TestTube2, color: "bg-purple-100 text-purple-600" },
  { label: "Notes", href: "notes", icon: FileText, color: "bg-indigo-100 text-indigo-600" },
  { label: "Immunizations", href: "immunizations", icon: Syringe, color: "bg-teal-100 text-teal-600" },
  { label: "Encounters", href: "encounters", icon: Clock, color: "bg-cyan-100 text-cyan-600" },
  { label: "Timeline", href: "timeline", icon: Clock, color: "bg-gray-100 text-gray-600" },
] as const;

export default function PatientChartPage() {
  const params = useParams<{ patientId: string }>();
  const router = useRouter();
  const patientId = params.patientId;
  const facility = useFacilityStore((s) => s.facility);

  const { data: patientData, isLoading: isLoadingPatient } = usePatient(patientId);
  const { data: encountersData } = useEncounters(patientId);
  const createEncounter = useCreateEncounter();

  const patient = patientData?.data;
  const encounters = encountersData?.data ?? [];
  const activeEncounter = encounters.find(
    (e) => e.attributes.status === "ACTIVE" || e.attributes.status === "IN_PROGRESS",
  );

  return (
    <EHRLayout>
      <PageShell title="Patient Chart">
        <div className="mb-4">
          <Link
            href="/queue"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to queue
          </Link>
        </div>

        {isLoadingPatient ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading patient data...</span>
          </div>
        ) : !patient ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <User className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">Patient not found</p>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Patient Summary Card */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <div className="flex items-start gap-4">
                <div className="w-14 h-14 rounded-full bg-blue-100 flex items-center justify-center shrink-0">
                  <User className="w-7 h-7 text-blue-600" />
                </div>
                <div className="flex-1 min-w-0">
                  <h2 className="text-lg font-semibold text-gray-900">
                    {patient.attributes.displayName}
                  </h2>
                  <div className="mt-1 flex flex-wrap gap-x-6 gap-y-1 text-sm text-gray-500">
                    <span>DOB: {patient.attributes.dateOfBirth}</span>
                    <span>Gender: {patient.attributes.gender}</span>
                    <span>CPID: {patient.attributes.cpid}</span>
                  </div>
                </div>
                {activeEncounter ? (
                  <Link
                    href={`/ehr/${patientId}/encounter/${activeEncounter.id}`}
                    className="px-4 py-2 bg-green-600 text-white text-sm font-medium rounded-lg hover:bg-green-700 transition-colors shrink-0"
                  >
                    Active Encounter
                  </Link>
                ) : (
                  <button
                    onClick={() => {
                      if (!facility) return;
                      createEncounter.mutate(
                        {
                          patientId,
                          facilityId: facility.id,
                          encounterType: "OUTPATIENT",
                        },
                        {
                          onSuccess: (data) => {
                            const newId = data?.data?.id;
                            if (newId) {
                              router.push(`/ehr/${patientId}/encounter/${newId}`);
                            }
                          },
                        },
                      );
                    }}
                    disabled={createEncounter.isPending || !facility}
                    className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors shrink-0 flex items-center gap-1.5"
                  >
                    {createEncounter.isPending ? (
                      <><Loader2 className="w-4 h-4 animate-spin" /> Starting...</>
                    ) : (
                      <><Activity className="w-4 h-4" /> Start Encounter</>
                    )}
                  </button>
                )}
              </div>
            </div>

            {/* Chart Sections Grid */}
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3">
              {CHART_SECTIONS.map((section) => {
                const Icon = section.icon;
                return (
                  <Link
                    key={section.href}
                    href={`/ehr/${patientId}/${section.href}`}
                    className="bg-white rounded-lg border border-gray-200 p-4 text-center hover:border-blue-300 hover:shadow-sm transition-all group"
                  >
                    <div
                      className={`w-10 h-10 rounded-lg ${section.color} flex items-center justify-center mx-auto mb-2`}
                    >
                      <Icon className="w-5 h-5" />
                    </div>
                    <p className="text-sm font-medium text-gray-700 group-hover:text-gray-900">
                      {section.label}
                    </p>
                  </Link>
                );
              })}
            </div>

            {/* Recent Encounters */}
            {encounters.length > 0 && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <h3 className="font-medium text-gray-900 mb-3">Recent Encounters</h3>
                <div className="space-y-2">
                  {encounters.slice(0, 5).map((enc) => (
                    <Link
                      key={enc.id}
                      href={`/ehr/${patientId}/encounter/${enc.id}`}
                      className="flex items-center justify-between p-3 rounded-lg border border-gray-100 hover:bg-gray-50 transition-colors"
                    >
                      <div>
                        <p className="text-sm font-medium text-gray-900">
                          {enc.attributes.encounterType}
                        </p>
                        <p className="text-xs text-gray-500">
                          {new Date(enc.attributes.startedAt).toLocaleString()}
                        </p>
                      </div>
                      <span
                        className={`px-2 py-0.5 text-xs rounded-full ${
                          enc.attributes.status === "ACTIVE" || enc.attributes.status === "IN_PROGRESS"
                            ? "bg-green-100 text-green-700"
                            : "bg-gray-100 text-gray-600"
                        }`}
                      >
                        {enc.attributes.status}
                      </span>
                    </Link>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
