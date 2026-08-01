"use client";

/**
 * Walk-in Registration — Patient search + queue entry creation.
 * Route: /queue/walk-in | pageTitle: "Walk-in Registration"
 *
 * New client capture uses {@link VitoClientRegistrationWizard} (registry templates + Vito BFF path).
 */

import { useEffect, useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, ArrowRight, ArrowRightLeft, Check, CircleCheck, ClipboardCheck, Loader2, Search, ShieldCheck, User, UserPlus } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { QueueWorkspaceHeader } from "@/components/queue/QueueWorkspaceHeader";
import { usePatient, usePatients, type PatientResource } from "@/hooks/queries/usePatients";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import type { QueueEntryResource } from "@/hooks/queries/useQueue";
import { getPatientDisplayName, getPatientQueueSummary } from "@/lib/queue-workflows";
import { VitoClientRegistrationWizard } from "@/components/registry/VitoClientRegistrationWizard";
import { BiometricVerifyField } from "@/components/biometric/BiometricVerifyField";
import { EnrolBiometricStep } from "@/components/biometric/EnrolBiometricStep";
import type { Modality } from "@/hooks/queries/useAbisBiometric";

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
  const [liveProbe, setLiveProbe] = useState<{ modality: Modality; probeBase64: string } | null>(null);
  const [queueReceipt, setQueueReceipt] = useState<{
    entryId: string;
    encounterPath: string;
    patientName: string;
    facilityName: string;
  } | null>(null);

  const [showNewPatient, setShowNewPatient] = useState(false);
  // True only for a patient just created via the registration wizard — they get
  // the ENROL step (register a first biometric), not the check-in VERIFY field.
  const [justRegistered, setJustRegistered] = useState(false);

  const {
    data: patientsData,
    isLoading: isSearching,
    isError: searchFailed,
  } = usePatients(searchSubmitted ? { search: searchSubmitted } : undefined);
  const { data: preselectedPatientData } = usePatient(preselectedPatientId ?? "");

  const patients = searchSubmitted ? (patientsData?.data ?? []) : [];

  useEffect(() => {
    if (preselectedPatientData?.data && !selectedPatient) {
      setSelectedPatient(preselectedPatientData.data);
      setShowNewPatient(false);
      setJustRegistered(false);
    }
  }, [preselectedPatientData?.data, selectedPatient]);

  function handleSearch(e: FormEvent) {
    e.preventDefault();
    setSearchSubmitted(searchTerm);
    setSelectedPatient(null);
    setShowNewPatient(false);
    setJustRegistered(false);
  }

  async function handleCreateEntry() {
    if (!facility) return;
    setIsSubmitting(true);
    setError(null);

    try {
      const patientId = selectedPatient?.id;

      if (!patientId) {
        setError("Please select a patient or complete Vito-backed registration first.");
        setIsSubmitting(false);
        return;
      }

      const patientCpid = selectedPatient?.attributes?.cpid ?? undefined;
      const response = await apiClient.post<ApiResponse<QueueEntryResource>>(
        "/internal/v1/queue/entries",
        {
          patient_id: patientId,
          facility_id: facility.id,
          priority: "NORMAL",
          queue_type: "WALK_IN",
          patient_cpid: patientCpid,
          // Optional live biometric probe verified server-side at check-in (ABIS seam):
          // MATCH → journey starts; NO_MATCH → PCT rejects (4xx, surfaced below);
          // UNAVAILABLE → check-in proceeds on the existing factor.
          biometric_subject_ref: liveProbe ? patientCpid : undefined,
          biometric_modality: liveProbe?.modality,
          biometric_probe_base64: liveProbe?.probeBase64,
        },
      );

      const params = new URLSearchParams();
      const journeyId = response.meta?.journey_id;
      const transactionId = response.meta?.core_transaction_id;
      if (journeyId) params.set("journey_id", journeyId);
      if (transactionId) params.set("transaction_id", transactionId);
      const query = params.toString();
      setQueueReceipt({
        entryId: response.data.id,
        encounterPath: query
          ? `/ehr/${patientId}/encounters?${query}`
          : `/ehr/${patientId}/encounters`,
        patientName: getPatientDisplayName(selectedPatient),
        facilityName: facility.name,
      });
    } catch (e) {
      const err = e as { status?: number; error?: { message?: string } };
      if (err?.status && err.status >= 400 && err.status < 500) {
        setError(
          err.error?.message
            ?? "Biometric did not match — check-in is blocked. Confirm the patient's identity another way, or check in without a biometric.",
        );
      } else {
        setError("Failed to create queue entry. Please try again.");
      }
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
        {queueReceipt ? (
          <section className="mx-auto max-w-2xl rounded-3xl border border-emerald-200 bg-white p-6 shadow-sm" aria-labelledby="queue-receipt-title">
            <div className="flex items-start gap-3">
              <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-emerald-100 text-emerald-700">
                <CircleCheck className="h-6 w-6" aria-hidden />
              </span>
              <div>
                <p className="text-xs font-bold uppercase tracking-[0.16em] text-emerald-700">Queue entry created</p>
                <h2 id="queue-receipt-title" className="mt-1 text-2xl font-semibold text-foreground">The patient is ready for the next step</h2>
                <p className="mt-2 text-sm text-muted-foreground">
                  {queueReceipt.patientName} was added to the walk-in queue at {queueReceipt.facilityName}.
                </p>
              </div>
            </div>
            <dl className="mt-5 grid gap-3 rounded-2xl bg-emerald-50 p-4 sm:grid-cols-2">
              <div><dt className="text-xs font-medium text-emerald-700">Queue reference</dt><dd className="mt-1 font-mono text-sm font-semibold text-emerald-950">{queueReceipt.entryId}</dd></div>
              <div><dt className="text-xs font-medium text-emerald-700">Next action</dt><dd className="mt-1 text-sm font-semibold text-emerald-950">Open the patient encounter</dd></div>
            </dl>
            <div className="mt-6 flex flex-wrap gap-3">
              <button type="button" onClick={() => router.push(queueReceipt.encounterPath)} className="inline-flex min-h-11 items-center gap-2 rounded-xl bg-primary px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-hover">
                Open patient encounter <ArrowRight className="h-4 w-4" aria-hidden />
              </button>
              <Link href="/queue" className="inline-flex min-h-11 items-center rounded-xl border border-border bg-white px-4 py-2.5 text-sm font-semibold text-foreground hover:bg-background">Return to queue</Link>
            </div>
          </section>
        ) : (
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
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to queue
          </Link>

          <div className="mx-auto max-w-2xl space-y-6">
            <ol className="grid gap-2 rounded-2xl border border-border bg-card p-3 sm:grid-cols-3" aria-label="Walk-in registration progress">
              {[
                { label: "Find patient", complete: Boolean(selectedPatient) },
                { label: "Verify identity", complete: Boolean(selectedPatient && (liveProbe || justRegistered)) },
                { label: "Add to queue", complete: false },
              ].map((step, index) => (
                <li key={step.label} className={`flex items-center gap-2 rounded-xl px-3 py-2 text-xs font-semibold ${selectedPatient || index === 0 ? "bg-primary-soft text-primary-hover" : "text-muted-foreground"}`}>
                  <span className="grid h-6 w-6 shrink-0 place-items-center rounded-full border border-current">{step.complete ? <Check className="h-3.5 w-3.5" aria-hidden /> : index + 1}</span>
                  {step.label}
                </li>
              ))}
            </ol>
            {selectedPatient ? (
              <section className="rounded-3xl border border-primary/25 bg-primary-soft p-5" aria-labelledby="selected-patient-title">
                <div className="flex items-start justify-between gap-3">
                  <div><p className="text-xs font-bold uppercase tracking-wide text-primary-hover">Selected patient</p><h2 id="selected-patient-title" className="mt-1 text-lg font-semibold text-foreground">{getPatientDisplayName(selectedPatient)}</h2></div>
                  <ShieldCheck className="h-5 w-5 text-primary" aria-label="Identity details shown under active work context" />
                </div>
                <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-3">
                  <div><dt className="text-xs text-muted-foreground">Date of birth</dt><dd className="mt-1 font-medium text-foreground">{selectedPatient.attributes.dateOfBirth || "Not recorded"}</dd></div>
                  <div><dt className="text-xs text-muted-foreground">Sex / gender</dt><dd className="mt-1 font-medium capitalize text-foreground">{selectedPatient.attributes.gender || "Not recorded"}</dd></div>
                  <div><dt className="text-xs text-muted-foreground">Impilo reference</dt><dd className="mt-1 font-medium text-foreground">{selectedPatient.attributes.cpid || selectedPatient.id}</dd></div>
                </dl>
                <button type="button" onClick={() => { setSelectedPatient(null); setLiveProbe(null); }} className="mt-4 text-xs font-semibold text-primary hover:text-primary-hover">Choose a different patient</button>
              </section>
            ) : null}

            {/* Patient Search */}
            <div className="bg-card rounded-lg border border-border p-5">
              <h3 className="font-medium text-foreground mb-3">Search Existing Patient</h3>
              <form onSubmit={handleSearch} className="flex gap-2">
                <div className="relative flex-1">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                  <input
                    type="text"
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    placeholder="Search by name, ID, or date of birth..."
                    className="w-full pl-10 pr-4 py-2.5 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                  />
                </div>
                <button
                  type="submit"
                  className="px-4 py-2.5 bg-neutral-100 text-foreground text-sm font-medium rounded-lg hover:bg-neutral-100 transition-colors"
                >
                  Search
                </button>
              </form>

              {isSearching && (
                <div className="flex items-center gap-2 mt-4 text-sm text-muted-foreground">
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Searching...
                </div>
              )}

              {searchSubmitted && !isSearching && searchFailed && (
                <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-3 text-center">
                  <p className="text-sm text-warning-foreground">
                    The patient registry is unreachable right now — this is not an empty result.
                    Retry shortly or contact support if it persists.
                  </p>
                </div>
              )}

              {searchSubmitted && !isSearching && !searchFailed && patients.length === 0 && (
                <div className="mt-4 p-3 bg-background rounded-lg text-center">
                  <p className="text-sm text-muted-foreground">No patients found</p>
                  <button
                    onClick={() => setShowNewPatient(true)}
                    className="mt-2 text-sm text-primary hover:text-primary-hover"
                  >
                    Register new client (Vito-backed wizard)
                  </button>
                </div>
              )}

              {!selectedPatient && patients.length > 0 && (
                <div className="mt-4 space-y-2">
                  {patients.map((patient) => (
                    <button
                      key={patient.id}
                      onClick={() => {
                        setSelectedPatient(patient);
                        setShowNewPatient(false);
                        setJustRegistered(false);
                      }}
                      className="w-full flex items-center gap-3 p-3 rounded-lg border border-border text-left transition-colors hover:border-primary/40 hover:bg-background"
                    >
                      <div className="w-8 h-8 rounded-full bg-neutral-100 flex items-center justify-center">
                        <User className="w-4 h-4 text-muted-foreground" />
                      </div>
                      <div>
                        <p className="text-sm font-medium text-foreground">{getPatientDisplayName(patient)}</p>
                        <p className="text-xs text-muted-foreground">{getPatientQueueSummary(patient)}</p>
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </div>

            {showNewPatient && (
              <div className="bg-card rounded-lg border border-border p-5 space-y-3">
                <h3 className="font-medium text-foreground">Vito-backed client registration</h3>
                <p className="text-xs text-muted-foreground">
                  Uses registry templates and controlled value sets. Duplicate search is mandatory before create in
                  production Tshepo policy — enforced here as a workflow step in the wizard.
                </p>
                <VitoClientRegistrationWizard
                  facilityId={facility?.id}
                  sourceWorkflow="QUEUE_WALK_IN"
                  onCancel={() => setShowNewPatient(false)}
                  onRegistered={(p) => {
                    setSelectedPatient(p);
                    setShowNewPatient(false);
                    setJustRegistered(true);
                  }}
                />
              </div>
            )}

            {/* Newly-registered patient → ENROL a first biometric (register).
                Existing/returning patient → VERIFY at check-in. Mutually exclusive. */}
            {selectedPatient && justRegistered && selectedPatient.attributes?.cpid ? (
              <EnrolBiometricStep
                subjectRef={selectedPatient.attributes.cpid}
                subjectLabel={getPatientDisplayName(selectedPatient)}
                disabled={isSubmitting}
              />
            ) : selectedPatient ? (
              <BiometricVerifyField
                label="Verify at check-in (optional)"
                onProbe={setLiveProbe}
                disabled={isSubmitting}
              />
            ) : null}

            {error && (
              <div className="p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-danger">{error}</div>
            )}

            {selectedPatient && (
              <button
                onClick={() => void handleCreateEntry()}
                disabled={isSubmitting}
                className="w-full py-2.5 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-primary/40 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
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
        )}
      </PageShell>
    </AppLayout>
  );
}
