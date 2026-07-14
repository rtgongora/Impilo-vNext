"use client";

import Link from "next/link";
import { useState } from "react";
import { UserPlus, CheckCircle, AlertTriangle, Plus, X } from "lucide-react";
import { StickyActionBar } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { VitoRestrictedLocationCapture } from "@/components/registry/VitoRestrictedLocationCapture";
import type { NdilaCoordinate } from "@/lib/ndila/ndila-client";
import {
  useCreateClientRegistration,
  useSubmitClientRegistration,
  useAddClientEvidence,
  useRecordVerificationReview,
  useRunClientMatching,
  useReviewClientMatchCandidate,
  useClientProfile,
  type RegistrationDraft,
  type ClientProfile,
  type MatchCandidate,
  type ClientRegistrationType,
  type ClientMatchReviewStatus,
} from "@/hooks/queries/useClientRegistry";

type WizardStep = 1 | 2 | 3 | 4;

const STEPS = [
  { num: 1, label: "Demographics" },
  { num: 2, label: "Review & Evidence" },
  { num: 3, label: "Verification" },
  { num: 4, label: "Complete" },
];

const REGISTRATION_TYPES: ClientRegistrationType[] = [
  "FACILITY_REGISTRATION",
  "PROVIDER_ASSISTED",
  "COMMUNITY_REGISTRATION",
  "OUTREACH_REGISTRATION",
  "SELF_INITIATED",
  "VIRTUAL_REGISTRATION",
];

const INITIATED_CHANNELS = ["IN_PERSON", "ONLINE", "MOBILE", "PORTAL"];

const SEX_OPTIONS = ["MALE", "FEMALE", "OTHER", "UNKNOWN"];

const ID_DOC_TYPES = ["NATIONAL_ID", "PASSPORT"];

const REVIEW_DECISIONS = ["APPROVED", "REJECTED", "NEEDS_MORE_INFO"];

function Stepper({ currentStep }: { currentStep: WizardStep }) {
  return (
    <div className="flex items-center">
      {STEPS.map((step, idx) => {
        const isCompleted = step.num < currentStep;
        const isActive = step.num === currentStep;
        const isPending = step.num > currentStep;
        const isLast = idx === STEPS.length - 1;

        return (
          <div key={step.num} className="flex flex-1 items-center">
            <div className="flex flex-col items-center gap-1">
              <div
                className={`flex h-8 w-8 items-center justify-center rounded-full text-xs font-semibold transition-colors ${
                  isCompleted
                    ? "bg-emerald-500 text-white"
                    : isActive
                    ? "bg-primary-hover text-white"
                    : isPending
                    ? "border-2 border-border bg-card text-muted-foreground"
                    : ""
                }`}
              >
                {isCompleted ? <CheckCircle className="h-4 w-4" /> : step.num}
              </div>
              <span
                className={`hidden text-xs font-medium sm:block ${
                  isActive ? "text-primary-hover" : isCompleted ? "text-primary" : "text-muted-foreground"
                }`}
              >
                {step.label}
              </span>
            </div>
            {!isLast && (
              <div
                className={`mx-1 h-0.5 flex-1 transition-colors ${
                  step.num < currentStep ? "bg-emerald-300" : "bg-neutral-100"
                }`}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}

function Field({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div className="space-y-0.5">
      <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">{label}</p>
      <p className="text-sm text-foreground">{value ?? "—"}</p>
    </div>
  );
}

interface Step1FormData {
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  sex: string;
  nationality: string;
  addressLine1: string;
  phone: string;
  idDocumentType: string;
  idDocumentNumber: string;
  registrationType: ClientRegistrationType;
  initiatedChannel: string;
  notes: string;
}

interface Step2State {
  evidenceType: string;
  evidenceReference: string;
  evidenceNotes: string;
}

interface Step3FormData {
  reviewType: string;
  decision: string;
  reviewNotes: string;
}

export default function NewRegistrationPage() {
  const [step, setStep] = useState<WizardStep>(1);
  const [error, setError] = useState("");
  const [profile, setProfile] = useState<ClientProfile | null>(null);
  const [registrationId, setRegistrationId] = useState<string | null>(null);
  const [matchCandidates, setMatchCandidates] = useState<MatchCandidate[]>([]);
  const [registrationLocation, setRegistrationLocation] = useState<NdilaCoordinate | null>(null);

  const [form1, setForm1] = useState<Step1FormData>({
    firstName: "",
    lastName: "",
    dateOfBirth: "",
    sex: "UNKNOWN",
    nationality: "",
    addressLine1: "",
    phone: "",
    idDocumentType: "NATIONAL_ID",
    idDocumentNumber: "",
    registrationType: "FACILITY_REGISTRATION",
    initiatedChannel: "IN_PERSON",
    notes: "",
  });

  const [step2, setStep2] = useState<Step2State>({
    evidenceType: "",
    evidenceReference: "",
    evidenceNotes: "",
  });

  const [step3Form, setStep3Form] = useState<Step3FormData>({
    reviewType: "IDENTITY_VERIFICATION",
    decision: "APPROVED",
    reviewNotes: "",
  });

  const healthId = profile?.master?.healthId;

  const createRegistration = useCreateClientRegistration();
  const submitRegistration = useSubmitClientRegistration();
  const addEvidence = useAddClientEvidence(healthId ?? "");
  const verificationReview = useRecordVerificationReview(healthId ?? "");
  const triggerMatch = useRunClientMatching(healthId ?? "");
  const reviewMatchCandidate = useReviewClientMatchCandidate();

  const profileQuery = useClientProfile(step >= 3 ? healthId : undefined);
  const liveProfile = profileQuery.data?.data ?? profile;

  function clearError() {
    setError("");
  }

  function updateForm1<K extends keyof Step1FormData>(key: K, value: Step1FormData[K]) {
    setForm1((prev) => ({ ...prev, [key]: value }));
  }

  async function handleStep1Submit(e: React.FormEvent) {
    e.preventDefault();
    clearError();
    const draft: RegistrationDraft = {
      registrationType: form1.registrationType,
      initiatedChannel: form1.initiatedChannel,
      firstName: form1.firstName || null,
      lastName: form1.lastName || null,
      dateOfBirth: form1.dateOfBirth || null,
      sex: form1.sex || null,
      phone: form1.phone || null,
      addressLine1: form1.addressLine1 || null,
      notes: form1.notes || null,
      metadata: {
        ...(form1.nationality ? { nationality: form1.nationality } : {}),
        ...(registrationLocation
          ? {
              registrationLocation: {
                latitude: registrationLocation.latitude,
                longitude: registrationLocation.longitude,
                source: "NDILA_IDENTITY_REGISTRATION",
              },
            }
          : {}),
      },
      ...(form1.idDocumentType === "NATIONAL_ID"
        ? { nationalIdReference: form1.idDocumentNumber || null }
        : { passportReference: form1.idDocumentNumber || null }),
    };
    try {
      const res = await createRegistration.mutateAsync(draft);
      const createdProfile = res.data;
      setProfile(createdProfile);
      const firstReg = createdProfile.registrations?.[0];
      if (firstReg?.registrationId) {
        setRegistrationId(firstReg.registrationId);
      }
      setStep(2);
    } catch {
      setError("Failed to create registration draft. Check that the BFF and client registry are reachable.");
    }
  }

  async function handleAddEvidence(e: React.FormEvent) {
    e.preventDefault();
    clearError();
    if (!healthId || !registrationId) return;
    try {
      await addEvidence.mutateAsync({
        registrationId,
        evidenceType: step2.evidenceType.trim(),
        evidenceReference: step2.evidenceReference.trim(),
        notes: step2.evidenceNotes.trim() || undefined,
      });
      setStep2({ evidenceType: "", evidenceReference: "", evidenceNotes: "" });
    } catch {
      setError("Failed to add evidence. Please retry.");
    }
  }

  async function handleSubmitForReview() {
    clearError();
    if (!registrationId) return;
    try {
      const res = await submitRegistration.mutateAsync(registrationId);
      setProfile(res.data);
      setStep(3);
    } catch {
      setError("Failed to submit registration for review. Please retry.");
    }
  }

  async function handleVerificationReview(e: React.FormEvent) {
    e.preventDefault();
    clearError();
    if (!healthId) return;
    try {
      await verificationReview.mutateAsync({
        registrationId: registrationId ?? undefined,
        reviewType: step3Form.reviewType,
        decision: step3Form.decision,
        notes: step3Form.reviewNotes.trim() || undefined,
      });
      setStep(4);
    } catch {
      setError("Failed to record verification review. Please retry.");
    }
  }

  async function handleTriggerMatch() {
    clearError();
    if (!healthId) return;
    try {
      const res = await triggerMatch.mutateAsync();
      setMatchCandidates((res.data ?? []) as MatchCandidate[]);
    } catch {
      setError("Match trigger failed. Please retry.");
    }
  }

  async function handleReviewCandidate(matchId: number, outcome: ClientMatchReviewStatus) {
    clearError();
    try {
      await reviewMatchCandidate.mutateAsync({ matchId, body: { outcome } });
      setMatchCandidates((prev) => prev.filter((c) => c.matchId !== matchId));
    } catch {
      setError("Failed to record match review decision. Please retry.");
    }
  }

  return (
    <AppLayout>
      <PageShell
        title="New client registration"
        subtitle="Register a new person in the national client registry"
        icon={<UserPlus className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <div className="flex flex-wrap items-center gap-3">
            <Link
              href="/operations/vito/registration"
              className="text-sm text-muted-foreground underline-offset-2 hover:text-foreground hover:underline"
            >
              ← Client registrations
            </Link>
          </div>

          <div className="rounded-2xl border border-border bg-card p-5">
            <Stepper currentStep={step} />
          </div>

          {error && (
            <div className="flex items-center gap-2 rounded-2xl border border-danger/28 bg-danger-soft px-4 py-3 text-sm text-danger">
              <AlertTriangle className="h-4 w-4 flex-shrink-0" />
              <span>{error}</span>
              <button
                type="button"
                onClick={clearError}
                className="ml-auto rounded p-0.5 hover:bg-red-100"
                aria-label="Dismiss error"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </div>
          )}

          {step === 1 && (
            <form
              onSubmit={handleStep1Submit}
              className="rounded-2xl border border-border bg-card p-5 space-y-5"
            >
              <h2 className="text-sm font-semibold text-foreground">Step 1 — Demographics</h2>

              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground">
                  Given name *
                  <input
                    required
                    value={form1.firstName}
                    onChange={(e) => updateForm1("firstName", e.target.value)}
                    className="rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                    placeholder="Given name"
                  />
                </label>

                <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground">
                  Family name *
                  <input
                    required
                    value={form1.lastName}
                    onChange={(e) => updateForm1("lastName", e.target.value)}
                    className="rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                    placeholder="Family name"
                  />
                </label>

                <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground">
                  Date of birth
                  <input
                    type="date"
                    value={form1.dateOfBirth}
                    onChange={(e) => updateForm1("dateOfBirth", e.target.value)}
                    className="rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                  />
                </label>

                <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground">
                  Sex
                  <select
                    value={form1.sex}
                    onChange={(e) => updateForm1("sex", e.target.value)}
                    className="rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                  >
                    {SEX_OPTIONS.map((s) => (
                      <option key={s} value={s}>
                        {s.charAt(0) + s.slice(1).toLowerCase()}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground">
                  Nationality
                  <input
                    value={form1.nationality}
                    onChange={(e) => updateForm1("nationality", e.target.value)}
                    className="rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                    placeholder="e.g. ZWE"
                  />
                </label>

                <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground">
                  Phone number
                  <input
                    type="tel"
                    value={form1.phone}
                    onChange={(e) => updateForm1("phone", e.target.value)}
                    className="rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                    placeholder="+263…"
                  />
                </label>

                <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground sm:col-span-2">
                  Address
                  <input
                    value={form1.addressLine1}
                    onChange={(e) => updateForm1("addressLine1", e.target.value)}
                    className="rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                    placeholder="Address line 1"
                  />
                </label>

                <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground">
                  ID document type
                  <select
                    value={form1.idDocumentType}
                    onChange={(e) => updateForm1("idDocumentType", e.target.value)}
                    className="rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                  >
                    {ID_DOC_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {t.replace(/_/g, " ")}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground">
                  ID document number
                  <input
                    value={form1.idDocumentNumber}
                    onChange={(e) => updateForm1("idDocumentNumber", e.target.value)}
                    className="rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                    placeholder="Document number"
                  />
                </label>

                <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground">
                  Registration type
                  <select
                    value={form1.registrationType}
                    onChange={(e) =>
                      updateForm1("registrationType", e.target.value as ClientRegistrationType)
                    }
                    className="rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                  >
                    {REGISTRATION_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {t.replace(/_/g, " ")}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground">
                  Channel
                  <select
                    value={form1.initiatedChannel}
                    onChange={(e) => updateForm1("initiatedChannel", e.target.value)}
                    className="rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                  >
                    {INITIATED_CHANNELS.map((c) => (
                      <option key={c} value={c}>
                        {c.replace(/_/g, " ")}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground col-span-full">
                  Notes
                  <textarea
                    rows={2}
                    value={form1.notes}
                    onChange={(e) => updateForm1("notes", e.target.value)}
                    className="rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300 resize-none"
                    placeholder="Optional notes"
                  />
                </label>
              </div>

              <VitoRestrictedLocationCapture
                registrationType={form1.registrationType}
                value={registrationLocation}
                onChange={setRegistrationLocation}
              />

              <StickyActionBar status="Step 1 of 4 — Demographics">
                <button
                  type="submit"
                  disabled={createRegistration.isPending}
                  className="inline-flex items-center gap-2 rounded-xl bg-primary-hover px-5 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
                >
                  {createRegistration.isPending ? "Saving draft…" : "Next — Review & Evidence"}
                </button>
              </StickyActionBar>
            </form>
          )}

          {step === 2 && profile && (
            <div className="space-y-5">
              <div className="rounded-2xl border border-border bg-card p-5 space-y-4">
                <h2 className="text-sm font-semibold text-foreground">Step 2 — Draft summary</h2>
                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  <Field label="Given name" value={profile.master?.firstName} />
                  <Field label="Family name" value={profile.master?.lastName} />
                  <Field label="Date of birth" value={profile.master?.dateOfBirth} />
                  <Field label="Sex" value={profile.master?.sex} />
                  <Field label="Health ID" value={profile.master?.healthId} />
                  <Field label="Status" value={profile.master?.lifecycleStatus} />
                </div>
                {registrationId && (
                  <p className="text-xs text-muted-foreground">
                    Registration ID:{" "}
                    <span className="font-mono">{registrationId}</span>
                  </p>
                )}
              </div>

              <div className="rounded-2xl border border-border bg-card p-5 space-y-4">
                <div className="flex items-center gap-2">
                  <Plus className="h-4 w-4 text-muted-foreground" />
                  <h2 className="text-sm font-semibold text-foreground">Add evidence</h2>
                </div>
                <form onSubmit={handleAddEvidence} className="space-y-3">
                  <div className="grid gap-3 sm:grid-cols-2">
                    <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground">
                      Evidence type *
                      <input
                        required
                        value={step2.evidenceType}
                        onChange={(e) => setStep2((p) => ({ ...p, evidenceType: e.target.value }))}
                        className="rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                        placeholder="e.g. NATIONAL_ID_SCAN"
                      />
                    </label>
                    <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground">
                      Document URL / reference *
                      <input
                        required
                        value={step2.evidenceReference}
                        onChange={(e) =>
                          setStep2((p) => ({ ...p, evidenceReference: e.target.value }))
                        }
                        className="rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                        placeholder="URL or base64 reference"
                      />
                    </label>
                    <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground sm:col-span-2">
                      Notes
                      <input
                        value={step2.evidenceNotes}
                        onChange={(e) =>
                          setStep2((p) => ({ ...p, evidenceNotes: e.target.value }))
                        }
                        className="rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                        placeholder="Optional notes"
                      />
                    </label>
                  </div>
                  <button
                    type="submit"
                    disabled={addEvidence.isPending}
                    className="inline-flex items-center gap-1.5 rounded-xl border border-border bg-card px-4 py-2 text-sm font-medium text-foreground hover:border-gray-400 hover:bg-background disabled:opacity-50"
                  >
                    <Plus className="h-4 w-4" />
                    {addEvidence.isPending ? "Adding…" : "Add evidence"}
                  </button>
                  {addEvidence.isSuccess && (
                    <p className="text-xs text-primary">Evidence added successfully.</p>
                  )}
                </form>

                {profile.evidence && profile.evidence.length > 0 && (
                  <div className="space-y-2">
                    <p className="text-xs font-medium text-muted-foreground">Attached evidence</p>
                    {profile.evidence.map((ev) => (
                      <div
                        key={ev.evidenceId}
                        className="flex items-center justify-between rounded-xl border border-border bg-background px-3 py-2 text-xs text-foreground"
                      >
                        <span className="font-medium">{ev.evidenceType}</span>
                        <span className="text-muted-foreground truncate max-w-[200px]">
                          {ev.evidenceReference}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <StickyActionBar status="Step 2 of 4 — Review & Evidence">
                <button
                  type="button"
                  onClick={() => setStep(1)}
                  className="rounded-xl border border-border px-4 py-2 text-sm font-medium text-muted-foreground hover:border-border hover:bg-background"
                >
                  ← Back
                </button>
                <button
                  type="button"
                  disabled={submitRegistration.isPending}
                  onClick={handleSubmitForReview}
                  className="inline-flex items-center gap-2 rounded-xl bg-primary-hover px-5 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
                >
                  {submitRegistration.isPending ? "Submitting…" : "Submit for review →"}
                </button>
              </StickyActionBar>
            </div>
          )}

          {step === 3 && (
            <div className="space-y-5">
              <div className="rounded-2xl border border-border bg-card p-5 space-y-4">
                <h2 className="text-sm font-semibold text-foreground">Step 3 — Verification</h2>

                {profileQuery.isLoading ? (
                  <p className="text-sm text-muted-foreground">Loading profile…</p>
                ) : liveProfile ? (
                  <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                    <Field label="Health ID" value={liveProfile.master?.healthId} />
                    <Field label="Status" value={liveProfile.master?.lifecycleStatus} />
                    <Field label="Verification" value={liveProfile.master?.verificationStatus} />
                    <Field
                      label="Identity assurance"
                      value={String(liveProfile.master?.identityAssuranceLevel ?? "—")}
                    />
                  </div>
                ) : null}
              </div>

              <div className="rounded-2xl border border-border bg-card p-5 space-y-4">
                <h2 className="text-sm font-semibold text-foreground">Record verification review</h2>
                <form onSubmit={handleVerificationReview} className="space-y-4">
                  <div className="grid gap-4 sm:grid-cols-2">
                    <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground">
                      Review type
                      <input
                        value={step3Form.reviewType}
                        onChange={(e) =>
                          setStep3Form((p) => ({ ...p, reviewType: e.target.value }))
                        }
                        className="rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                        placeholder="e.g. IDENTITY_VERIFICATION"
                      />
                    </label>

                    <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground">
                      Outcome *
                      <select
                        required
                        value={step3Form.decision}
                        onChange={(e) =>
                          setStep3Form((p) => ({ ...p, decision: e.target.value }))
                        }
                        className="rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                      >
                        {REVIEW_DECISIONS.map((d) => (
                          <option key={d} value={d}>
                            {d.replace(/_/g, " ")}
                          </option>
                        ))}
                      </select>
                    </label>

                    <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground sm:col-span-2">
                      Notes
                      <textarea
                        rows={2}
                        value={step3Form.reviewNotes}
                        onChange={(e) =>
                          setStep3Form((p) => ({ ...p, reviewNotes: e.target.value }))
                        }
                        className="rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300 resize-none"
                        placeholder="Optional reviewer notes"
                      />
                    </label>
                  </div>

                  <button
                    type="submit"
                    disabled={verificationReview.isPending}
                    className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-5 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
                  >
                    {verificationReview.isPending ? "Recording…" : "Record review & continue →"}
                  </button>
                </form>
              </div>

              <div className="rounded-2xl border border-border bg-card p-5 space-y-4">
                <div className="flex items-center justify-between">
                  <h2 className="text-sm font-semibold text-foreground">Identity match check</h2>
                  <button
                    type="button"
                    disabled={triggerMatch.isPending || !healthId}
                    onClick={handleTriggerMatch}
                    className="inline-flex items-center gap-1.5 rounded-xl border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:border-gray-400 hover:bg-background disabled:opacity-50"
                  >
                    {triggerMatch.isPending ? "Matching…" : "Trigger match check"}
                  </button>
                </div>

                {triggerMatch.isSuccess && matchCandidates.length === 0 && (
                  <div className="flex items-center gap-2 rounded-xl bg-success-soft px-4 py-3 text-sm text-primary-hover">
                    <CheckCircle className="h-4 w-4 flex-shrink-0" />
                    No duplicate matches found.
                  </div>
                )}

                {matchCandidates.length > 0 && (
                  <div className="space-y-2">
                    <p className="text-xs font-medium text-warning-foreground">
                      {matchCandidates.length} potential match{matchCandidates.length !== 1 ? "es" : ""} found — review each candidate:
                    </p>
                    {matchCandidates.map((candidate) => (
                      <div
                        key={candidate.matchId}
                        className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-warning/35 bg-warning-soft px-4 py-3"
                      >
                        <div className="space-y-0.5 text-xs">
                          <p className="font-medium text-foreground">
                            Candidate:{" "}
                            <span className="font-mono">{candidate.candidateHealthId}</span>
                          </p>
                          <p className="text-muted-foreground">
                            Score: {candidate.matchScore}
                            {candidate.matchReasonSummary && ` — ${candidate.matchReasonSummary}`}
                          </p>
                        </div>
                        <div className="flex gap-2">
                          <button
                            type="button"
                            disabled={reviewMatchCandidate.isPending}
                            onClick={() =>
                              handleReviewCandidate(candidate.matchId, "CONFIRMED_DISTINCT")
                            }
                            className="rounded-xl border border-success/25 bg-card px-3 py-1 text-xs font-medium text-primary-hover hover:bg-success-soft disabled:opacity-50"
                          >
                            Not a duplicate
                          </button>
                          <button
                            type="button"
                            disabled={reviewMatchCandidate.isPending}
                            onClick={() =>
                              handleReviewCandidate(candidate.matchId, "CONFIRMED_DUPLICATE")
                            }
                            className="rounded-xl border border-danger/28 bg-card px-3 py-1 text-xs font-medium text-danger hover:bg-danger-soft disabled:opacity-50"
                          >
                            Confirmed duplicate
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div className="flex justify-start">
                <button
                  type="button"
                  onClick={() => setStep(2)}
                  className="rounded-xl border border-border px-4 py-2 text-sm font-medium text-muted-foreground hover:border-border hover:bg-background"
                >
                  ← Back
                </button>
              </div>
            </div>
          )}

          {step === 4 && (
            <div className="space-y-5">
              <div className="rounded-2xl border border-success/25 bg-success-soft p-5 space-y-3">
                <div className="flex items-center gap-3">
                  <CheckCircle className="h-6 w-6 text-primary" />
                  <h2 className="text-base font-semibold text-primary-hover">
                    Registration complete
                  </h2>
                </div>
                {healthId && (
                  <p className="text-sm text-primary-hover">
                    Health ID:{" "}
                    <span className="font-mono font-semibold">{healthId}</span>
                  </p>
                )}
              </div>

              {liveProfile && (
                <div className="rounded-2xl border border-border bg-card p-5 space-y-4">
                  <h2 className="text-sm font-semibold text-foreground">Final profile</h2>
                  <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                    <Field label="Health ID" value={liveProfile.master?.healthId} />
                    <Field
                      label="Full name"
                      value={[liveProfile.master?.firstName, liveProfile.master?.lastName]
                        .filter(Boolean)
                        .join(" ")}
                    />
                    <Field label="Date of birth" value={liveProfile.master?.dateOfBirth} />
                    <Field label="Sex" value={liveProfile.master?.sex} />
                    <Field label="Lifecycle status" value={liveProfile.master?.lifecycleStatus} />
                    <Field
                      label="Verification status"
                      value={liveProfile.master?.verificationStatus}
                    />
                    <Field
                      label="Assurance level"
                      value={String(liveProfile.master?.identityAssuranceLevel ?? "—")}
                    />
                    <Field
                      label="Golden record"
                      value={liveProfile.master?.goldenRecordFlag ? "Yes" : "No"}
                    />
                  </div>
                </div>
              )}

              <div className="rounded-2xl border border-border bg-card p-5 space-y-3">
                <h2 className="text-sm font-semibold text-foreground">Next steps</h2>
                <div className="flex flex-wrap gap-3">
                  {healthId && (
                    <Link
                      href={`/operations/vito/cards?healthId=${encodeURIComponent(healthId)}`}
                      className="inline-flex items-center gap-2 rounded-xl border border-primary/25 bg-primary-soft px-4 py-2 text-sm font-medium text-impilo-800 hover:border-impilo-300"
                    >
                      Request smart card →
                    </Link>
                  )}
                  <Link
                    href="/operations/vito/issuance"
                    className="inline-flex items-center gap-2 rounded-xl border border-border bg-card px-4 py-2 text-sm font-medium text-foreground hover:border-border hover:bg-background"
                  >
                    Go to issuance queue →
                  </Link>
                  <Link
                    href="/operations/vito/registration/new"
                    className="inline-flex items-center gap-2 rounded-xl border border-border bg-card px-4 py-2 text-sm font-medium text-foreground hover:border-border hover:bg-background"
                    onClick={() => {
                      setStep(1);
                      setProfile(null);
                      setRegistrationId(null);
                      setMatchCandidates([]);
                      setError("");
                    }}
                  >
                    Register another client
                  </Link>
                </div>
              </div>
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
