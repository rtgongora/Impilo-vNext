"use client";

/**
 * Walk-in Registration — Patient search + queue entry creation.
 * Route: /queue/walk-in | pageTitle: "Walk-in Registration"
 */

import { useEffect, useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, ArrowRightLeft, ClipboardCheck, Loader2, Search, User, UserPlus } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { QueueWorkspaceHeader } from "@/components/queue/QueueWorkspaceHeader";
import { usePatient, usePatients, type PatientResource } from "@/hooks/queries/usePatients";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import type { QueueEntryResource } from "@/hooks/queries/useQueue";
import { getPatientDisplayName, getPatientQueueSummary } from "@/lib/queue-workflows";

export default function WalkInPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const facility = useFacilityStore((s) => s.facility);
  const preselectedPatientId = searchParams.get("patientId");

  const [searchTerm, setSearchTerm] = useState("");
  const [searchSubmitted, setSearchSubmitted] = useState("");
  const [selectedPatient, setSelectedPatient] = useState<PatientResource | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // New patient fields
  const [showNewPatient, setShowNewPatient] = useState(false);
  const [newName, setNewName] = useState("");
  const [newDob, setNewDob] = useState("");
  const [newGender, setNewGender] = useState("male");

  const { data: patientsData, isLoading: isSearching } = usePatients(
    searchSubmitted ? { search: searchSubmitted } : undefined,
  );
  const { data: preselectedPatientData } = usePatient(preselectedPatientId ?? "");

  const patients = searchSubmitted ? (patientsData?.data ?? []) : [];

  useEffect(() => {
    if (preselectedPatientData?.data && !selectedPatient) {
      setSelectedPatient(preselectedPatientData.data);
      setShowNewPatient(false);
    }
  }, [preselectedPatientData?.data, selectedPatient]);

  function handleSearch(e: FormEvent) {
    e.preventDefault();
    setSearchSubmitted(searchTerm);
    setSelectedPatient(null);
    setShowNewPatient(false);
  }

  async function handleCreateEntry() {
    if (!facility) return;
    setIsSubmitting(true);
    setError(null);

    try {
      let patientId = selectedPatient?.id;

      // If creating a new patient, first register them
      if (showNewPatient && !patientId) {
        const newPatientRes = await apiClient.post<ApiResponse<PatientResource>>(
          "/internal/v1/patients",
          {
            displayName: newName,
            dateOfBirth: newDob,
            gender: newGender,
          },
        );
        patientId = newPatientRes.data.id;
      }

      if (!patientId) {
        setError("Please select or register a patient first.");
        setIsSubmitting(false);
        return;
      }

      const patientCpid = selectedPatient?.attributes?.cpid ?? undefined;
      await apiClient.post<ApiResponse<QueueEntryResource>>(
        "/internal/v1/queue/entries",
        {
          patient_id: patientId,
          facility_id: facility.id,
          priority: "NORMAL",
          queue_type: "WALK_IN",
          patient_cpid: patientCpid,
        },
      );

      router.push("/queue");
    } catch {
      setError("Failed to create queue entry. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <AppLayout>
      <PageShell
        title="Walk-in Registration"
        subtitle={facility ? `${facility.name}` : "Search for an existing patient or register a new one"}
      >
        <div className="space-y-6">
          <QueueWorkspaceHeader
            badge="Walk-in intake"
            badgeIcon={UserPlus}
            title="Search first, select the patient, then create the facility queue entry here"
            description="Walk-in registration now stays tied to facility context and can receive patients directly from queue search, so staff do not have to repeat the lookup."
            facilityName={facility?.name}
            actions={[
              { href: "/queue", label: "Queue Workboard", icon: ArrowRightLeft },
              { href: "/queue/search", label: "Patient Search", icon: Search, tone: "secondary" },
              { href: "/queue/waiting", label: "Waiting Room", icon: ClipboardCheck, tone: "secondary" },
            ]}
            metrics={[
              {
                label: "Selected patient",
                value: selectedPatient ? "Ready" : "Pending",
                detail: selectedPatient
                  ? `${getPatientDisplayName(selectedPatient)} will be queued into ${facility?.name ?? "the active facility"}.`
                  : "Search the MPI or register a new patient before creating the queue entry.",
              },
            ]}
          />

          <Link
            href="/queue"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to queue
          </Link>
        

        <div className="max-w-2xl space-y-6">
          {selectedPatient ? (
            <div className="rounded-3xl border border-impilo-200 bg-impilo-50 p-4 text-sm text-impilo-800">
              <p className="font-medium">Selected patient</p>
              <p className="mt-1">{getPatientDisplayName(selectedPatient)}</p>
              <p className="mt-1 text-xs text-impilo-700">{getPatientQueueSummary(selectedPatient)}</p>
            </div>
          ) : null}

          {/* Patient Search */}
          <div className="bg-white rounded-lg border border-gray-200 p-5">
            <h3 className="font-medium text-gray-900 mb-3">Search Existing Patient</h3>
            <form onSubmit={handleSearch} className="flex gap-2">
              <div className="relative flex-1">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                <input
                  type="text"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  placeholder="Search by name, ID, or date of birth..."
                  className="w-full pl-10 pr-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400 focus:border-impilo-400"
                />
              </div>
              <button
                type="submit"
                className="px-4 py-2.5 bg-gray-100 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-200 transition-colors"
              >
                Search
              </button>
            </form>

            {isSearching && (
              <div className="flex items-center gap-2 mt-4 text-sm text-gray-500">
                <Loader2 className="w-4 h-4 animate-spin" />
                Searching...
              </div>
            )}

            {searchSubmitted && !isSearching && patients.length === 0 && (
              <div className="mt-4 p-3 bg-gray-50 rounded-lg text-center">
                <p className="text-sm text-gray-500">No patients found</p>
                <button
                  onClick={() => setShowNewPatient(true)}
                  className="mt-2 text-sm text-impilo-500 hover:text-impilo-700"
                >
                  Register new patient
                </button>
              </div>
            )}

            {patients.length > 0 && (
              <div className="mt-4 space-y-2">
                {patients.map((patient) => (
                  <button
                    key={patient.id}
                    onClick={() => {
                      setSelectedPatient(patient);
                      setShowNewPatient(false);
                    }}
                    className={`w-full flex items-center gap-3 p-3 rounded-lg border text-left transition-colors ${
                      selectedPatient?.id === patient.id
                        ? "border-impilo-400 bg-impilo-50"
                        : "border-gray-200 hover:border-gray-300 hover:bg-gray-50"
                    }`}
                  >
                    <div className="w-8 h-8 rounded-full bg-gray-200 flex items-center justify-center">
                      <User className="w-4 h-4 text-gray-500" />
                    </div>
                    <div>
                      <p className="text-sm font-medium text-gray-900">
                        {getPatientDisplayName(patient)}
                      </p>
                      <p className="text-xs text-gray-500">
                        {getPatientQueueSummary(patient)}
                      </p>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* New Patient Form */}
          {showNewPatient && (
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="font-medium text-gray-900 mb-3">Register New Patient</h3>
              <div className="space-y-3">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Full Name
                  </label>
                  <input
                    type="text"
                    required
                    value={newName}
                    onChange={(e) => setNewName(e.target.value)}
                    placeholder="Patient full name"
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                  />
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      Date of Birth
                    </label>
                    <input
                      type="date"
                      required
                      value={newDob}
                      onChange={(e) => setNewDob(e.target.value)}
                      className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      Gender
                    </label>
                    <select
                      value={newGender}
                      onChange={(e) => setNewGender(e.target.value)}
                      className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                    >
                      <option value="male">Male</option>
                      <option value="female">Female</option>
                      <option value="other">Other</option>
                    </select>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Actions */}
          {error && (
            <div className="p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700">
              {error}
            </div>
          )}

          {(selectedPatient || (showNewPatient && newName && newDob)) && (
            <button
              onClick={handleCreateEntry}
              disabled={isSubmitting}
              className="w-full py-2.5 bg-impilo-500 text-white text-sm font-medium rounded-lg hover:bg-impilo-600 focus:outline-none focus:ring-2 focus:ring-impilo-400 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Adding to queue...
                </>
              ) : (
                <>
                  <UserPlus className="w-4 h-4" />
                  Add to Queue
                </>
              )}
            </button>
          )}
        </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
