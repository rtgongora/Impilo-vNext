"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import {
  ArrowLeft,
  BadgeCheck,
  FileClock,
  Fingerprint,
  GitMerge,
  Link2,
  Loader2,
  ShieldCheck,
  UserCog,
  Users,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { ClientIntakeStatusBadges } from "@/components/registry/ClientIntakeStatusBadges";
import { PageShell } from "@/components/PageShell";
import {
  useAddAuthorizationLink,
  useAddClientEvidence,
  useAddClientRelationship,
  useClientProfile,
  useCreateClientMergeCase,
  useCreateStewardshipAction,
  useDecideClientMergeCase,
  useRecordClientCorrection,
  useRecordVerificationReview,
  useReviewClientMatchCandidate,
  useRunClientMatching,
  useSubmitClientRegistration,
  useUpdateStewardshipAction,
} from "@/hooks/queries/useClientRegistry";
import { useIssuanceQueue } from "@/hooks/queries/useVitoIssuance";

const tabs = [
  { id: "master", label: "Master profile" },
  { id: "registrations", label: "Registrations" },
  { id: "evidence", label: "Evidence / verification" },
  { id: "matching", label: "Matching / merge" },
  { id: "relationships", label: "Relationships" },
  { id: "authorisation", label: "Authorisation links" },
  { id: "stewardship", label: "Stewardship" },
  { id: "audit", label: "Audit / history" },
] as const;

type TabId = (typeof tabs)[number]["id"];

const inputClass =
  "w-full rounded-xl border border-border px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-slate-500";

function labelize(value: string | null | undefined) {
  if (!value) return "Not stated";
  return value.replaceAll("_", " ");
}

function toneForStatus(value: string) {
  if (value.includes("ACTIVE") || value.includes("VERIFIED")) return "bg-success-soft text-primary-hover border-success/25";
  if (value.includes("PROVISIONAL") || value.includes("PENDING")) return "bg-warning-soft text-warning-foreground border-warning/35";
  if (value.includes("FLAGGED") || value.includes("REVIEW")) return "bg-danger-soft text-danger border-danger/28";
  if (value.includes("MERGED") || value.includes("INACTIVE")) return "bg-neutral-100 text-foreground border-border";
  return "bg-neutral-100 text-foreground border-border";
}

export default function ClientDetailPage() {
  const params = useParams();
  const id = params.id as string;
  const [activeTab, setActiveTab] = useState<TabId>("master");
  const [selectedRegistrationId, setSelectedRegistrationId] = useState("");
  const [selectedMatchId, setSelectedMatchId] = useState("");
  const [selectedMergeCaseId, setSelectedMergeCaseId] = useState("");
  const [evidenceForm, setEvidenceForm] = useState({
    evidenceType: "SELF_ASSERTED_PROFILE",
    evidenceReference: "",
    verificationState: "PARTIALLY_VERIFIED",
    notes: "",
  });
  const [verificationForm, setVerificationForm] = useState({
    reviewType: "IDENTITY_REVIEW",
    decision: "VERIFIED",
    notes: "",
    identityAssuranceLevel: "2",
    issueImpiloId: true,
  });
  const [relationshipForm, setRelationshipForm] = useState({
    relatedClientHealthId: "",
    relationshipType: "GUARDIAN_OF",
    notes: "",
  });
  const [authorisationForm, setAuthorisationForm] = useState({
    authorisationType: "SELF_REGISTRATION_LINK",
    referenceId: "",
    status: "ACTIVE",
  });
  const [stewardshipForm, setStewardshipForm] = useState({
    actionType: "VERIFY_IDENTITY",
    dueDate: "",
    owner: "",
    completionNotes: "",
  });
  const [correctionForm, setCorrectionForm] = useState({
    firstName: "",
    middleName: "",
    lastName: "",
    phone: "",
    email: "",
    notes: "",
    requireReview: true,
  });

  const profileQuery = useClientProfile(id);
  const issuanceQueue = useIssuanceQueue(undefined, 0, 50);
  const profile = profileQuery.data?.data;
  const master = profile?.master;
  const latestRegistration = profile?.registrations[0];
  const clientIssuance = issuanceQueue.data?.data?.items?.find((request) => request.healthId === id);

  const submitRegistration = useSubmitClientRegistration();
  const addEvidence = useAddClientEvidence(id);
  const recordVerificationReview = useRecordVerificationReview(id);
  const runMatching = useRunClientMatching(id);
  const reviewMatchCandidate = useReviewClientMatchCandidate();
  const createMergeCase = useCreateClientMergeCase();
  const decideMergeCase = useDecideClientMergeCase();
  const addRelationship = useAddClientRelationship(id);
  const addAuthorizationLink = useAddAuthorizationLink(id);
  const createStewardshipAction = useCreateStewardshipAction(id);
  const updateStewardshipAction = useUpdateStewardshipAction();
  const recordCorrection = useRecordClientCorrection(id);

  const pendingRegistrations = profile?.registrations.filter((registration) =>
    ["DRAFT", "INITIATED", "PARTIALLY_CAPTURED"].includes(registration.workflowState),
  ) ?? [];
  const openStewardshipActions = profile?.stewardshipActions.filter((action) => action.status === "OPEN" || action.status === "IN_PROGRESS") ?? [];

  const summaryCards = useMemo(
    () => [
      {
        title: "Registrations",
        value: profile?.registrations.length ?? 0,
        icon: FileClock,
      },
      {
        title: "Evidence",
        value: profile?.evidence.length ?? 0,
        icon: Fingerprint,
      },
      {
        title: "Match candidates",
        value: profile?.matchCandidates.length ?? 0,
        icon: GitMerge,
      },
      {
        title: "Open stewardship",
        value: openStewardshipActions.length,
        icon: UserCog,
      },
    ],
    [openStewardshipActions.length, profile],
  );

  return (
    <AppLayout>
      <PageShell
        title="Client Identity Workspace"
        subtitle="Identity lifecycle, provenance, matching, relationship, and stewardship operations for one client"
      >
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <Link href="/registry/clients" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" />
            Back to client registry
          </Link>
          <Link
            href="/operations/vito"
            className="inline-flex items-center gap-2 rounded-xl border border-border px-4 py-2 text-sm font-medium text-foreground hover:border-slate-400 hover:text-foreground"
          >
            Stewardship console
            <Users className="h-4 w-4" />
          </Link>
        </div>

        {profileQuery.isLoading ? (
          <div className="flex items-center justify-center py-16 text-muted-foreground">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" />
            Loading client workspace...
          </div>
        ) : profileQuery.isError || !profile || !master ? (
          <div className="rounded-2xl border border-danger/28 bg-danger-soft p-6 text-center text-sm text-danger">
            Client identity workspace could not be loaded.
          </div>
        ) : (
          <div className="space-y-6">
            <div className="rounded-3xl border border-border bg-card p-6">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="text-2xl font-semibold text-foreground">
                      {[master.firstName, master.middleName, master.lastName].filter(Boolean).join(" ") || "Unnamed client"}
                    </h2>
                    <span className={`rounded-full border px-3 py-1 text-xs font-semibold ${toneForStatus(master.lifecycleStatus)}`}>
                      {labelize(master.lifecycleStatus)}
                    </span>
                    <ClientIntakeStatusBadges
                      input={{
                        verificationStatus: master.verificationStatus,
                        latestRegistrationType: latestRegistration?.registrationType,
                        latestRegistrationChannel: latestRegistration?.initiatedChannel,
                        lifecycleStatus: master.lifecycleStatus,
                        issuanceState: clientIssuance?.state,
                      }}
                      testIdPrefix="client-workspace-intake"
                    />
                  </div>
                  <p className="mt-2 text-sm text-muted-foreground">
                    {master.impiloId ?? "No Impilo ID yet"} • CRID {master.crid.slice(0, 8)} • Assurance {master.identityAssuranceLevel}
                  </p>
                </div>
                <div className="grid gap-3 text-sm md:grid-cols-2 xl:grid-cols-4">
                  {summaryCards.map((card) => {
                    const Icon = card.icon;
                    return (
                      <div key={card.title} className="rounded-2xl bg-background p-4">
                        <div className="flex items-center justify-between">
                          <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{card.title}</p>
                          <Icon className="h-4 w-4 text-muted-foreground" />
                        </div>
                        <p className="mt-2 text-2xl font-semibold text-foreground">{card.value}</p>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>

            <div className="flex flex-wrap gap-2">
              {tabs.map((tab) => (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => setActiveTab(tab.id)}
                  className={`rounded-full px-4 py-2 text-sm font-medium transition-colors ${
                    activeTab === tab.id ? "bg-neutral-900 text-white" : "bg-card text-muted-foreground hover:bg-background hover:text-foreground"
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            {activeTab === "master" ? (
              <div className="grid gap-4 lg:grid-cols-[1.1fr_0.9fr]">
                <div className="rounded-2xl border border-border bg-card p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Canonical profile</h3>
                  <dl className="mt-4 grid gap-4 md:grid-cols-2">
                    <div>
                      <dt className="text-xs text-muted-foreground">Date of birth</dt>
                      <dd className="mt-1 font-medium text-foreground">{master.dateOfBirth ?? "Not stated"}</dd>
                    </div>
                    <div>
                      <dt className="text-xs text-muted-foreground">Sex / gender</dt>
                      <dd className="mt-1 font-medium text-foreground">{master.sex ?? "Not stated"}</dd>
                    </div>
                    <div>
                      <dt className="text-xs text-muted-foreground">Phone</dt>
                      <dd className="mt-1 font-medium text-foreground">{String(master.contacts.phone ?? "Not stated")}</dd>
                    </div>
                    <div>
                      <dt className="text-xs text-muted-foreground">Email</dt>
                      <dd className="mt-1 font-medium text-foreground">{String(master.contacts.email ?? "Not stated")}</dd>
                    </div>
                    <div className="md:col-span-2">
                      <dt className="text-xs text-muted-foreground">Address</dt>
                      <dd className="mt-1 font-medium text-foreground">
                        {[
                          String(master.address.addressLine1 ?? ""),
                          String(master.address.addressLine2 ?? ""),
                          String(master.address.city ?? ""),
                          String(master.address.district ?? ""),
                          String(master.address.province ?? ""),
                        ]
                          .filter((value) => value && value !== "undefined")
                          .join(", ") || "Not stated"}
                      </dd>
                    </div>
                  </dl>
                </div>

                <div className="rounded-2xl border border-border bg-card p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Correction / amendment</h3>
                  <form
                    className="mt-4 space-y-3"
                    onSubmit={(event) => {
                      event.preventDefault();
                      recordCorrection.mutate(correctionForm);
                    }}
                  >
                    <div className="grid gap-3 md:grid-cols-2">
                      <input placeholder="First name" value={correctionForm.firstName} onChange={(event) => setCorrectionForm((current) => ({ ...current, firstName: event.target.value }))} className={inputClass} />
                      <input placeholder="Middle name" value={correctionForm.middleName} onChange={(event) => setCorrectionForm((current) => ({ ...current, middleName: event.target.value }))} className={inputClass} />
                      <input placeholder="Last name" value={correctionForm.lastName} onChange={(event) => setCorrectionForm((current) => ({ ...current, lastName: event.target.value }))} className={inputClass} />
                      <input placeholder="Phone" value={correctionForm.phone} onChange={(event) => setCorrectionForm((current) => ({ ...current, phone: event.target.value }))} className={inputClass} />
                      <input placeholder="Email" value={correctionForm.email} onChange={(event) => setCorrectionForm((current) => ({ ...current, email: event.target.value }))} className={`${inputClass} md:col-span-2`} />
                    </div>
                    <textarea placeholder="Correction notes" value={correctionForm.notes} onChange={(event) => setCorrectionForm((current) => ({ ...current, notes: event.target.value }))} rows={3} className={inputClass} />
                    <label className="flex items-center gap-3 rounded-xl border border-border bg-background px-4 py-3 text-sm text-foreground">
                      <input
                        type="checkbox"
                        checked={correctionForm.requireReview}
                        onChange={(event) => setCorrectionForm((current) => ({ ...current, requireReview: event.target.checked }))}
                        className="h-4 w-4 rounded border-border"
                      />
                      Route this demographic change through stewardship review instead of applying it immediately.
                    </label>
                    <button type="submit" className="rounded-xl bg-neutral-900 px-4 py-2.5 text-sm font-medium text-white hover:bg-slate-700">
                      Record correction
                    </button>
                  </form>
                </div>
              </div>
            ) : null}

            {activeTab === "registrations" ? (
              <div className="grid gap-4 lg:grid-cols-[1.1fr_0.9fr]">
                <div className="rounded-2xl border border-border bg-card p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Registration history</h3>
                  <div className="mt-4 space-y-3">
                    {profile.registrations.map((registration) => (
                      <div key={registration.registrationId} className="rounded-2xl border border-border p-4">
                        <div className="flex flex-wrap items-start justify-between gap-3">
                          <div>
                            <p className="font-medium text-foreground">{labelize(registration.registrationType)}</p>
                            <p className="mt-1 text-xs text-muted-foreground">
                              {labelize(registration.workflowState)} • {labelize(registration.initiatedChannel)}
                            </p>
                          </div>
                          {["DRAFT", "INITIATED", "PARTIALLY_CAPTURED"].includes(registration.workflowState) ? (
                            <button
                              type="button"
                              onClick={() => submitRegistration.mutate(registration.registrationId)}
                              className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:border-slate-400 hover:text-foreground"
                            >
                              Submit
                            </button>
                          ) : null}
                        </div>
                        <p className="mt-3 text-sm text-muted-foreground">{registration.notes ?? "No notes recorded."}</p>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="rounded-2xl border border-border bg-card p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Identifiers</h3>
                  <div className="mt-4 space-y-3">
                    {profile.identifiers.map((identifier) => (
                      <div key={identifier.identifierId} className="rounded-2xl bg-background p-4">
                        <div className="flex items-center justify-between gap-3">
                          <p className="font-medium text-foreground">{labelize(identifier.identifierType)}</p>
                          <span className={`rounded-full border px-2.5 py-1 text-xs font-semibold ${toneForStatus(identifier.status)}`}>
                            {labelize(identifier.status)}
                          </span>
                        </div>
                        <p className="mt-2 font-mono text-sm text-foreground">{identifier.identifierValue}</p>
                        <p className="mt-1 text-xs text-muted-foreground">{identifier.source ?? "Source not stated"}</p>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            ) : null}

            {activeTab === "evidence" ? (
              <div className="grid gap-4 lg:grid-cols-[1.1fr_0.9fr]">
                <div className="rounded-2xl border border-border bg-card p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Evidence dossier</h3>
                  <div className="mt-4 space-y-3">
                    {profile.evidence.map((evidence) => (
                      <div key={evidence.evidenceId} className="rounded-2xl border border-border p-4">
                        <div className="flex items-center justify-between gap-3">
                          <p className="font-medium text-foreground">{labelize(evidence.evidenceType)}</p>
                          <span className={`rounded-full border px-2.5 py-1 text-xs font-semibold ${toneForStatus(evidence.verificationState)}`}>
                            {labelize(evidence.verificationState)}
                          </span>
                        </div>
                        <p className="mt-2 text-sm text-foreground">{evidence.evidenceReference}</p>
                        <p className="mt-1 text-xs text-muted-foreground">{evidence.notes ?? "No notes recorded."}</p>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="space-y-4">
                  <div className="rounded-2xl border border-border bg-card p-5">
                    <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Add evidence</h3>
                    <form
                      className="mt-4 space-y-3"
                      onSubmit={(event) => {
                        event.preventDefault();
                        addEvidence.mutate({
                          registrationId: selectedRegistrationId || pendingRegistrations[0]?.registrationId,
                          evidenceType: evidenceForm.evidenceType,
                          evidenceReference: evidenceForm.evidenceReference,
                          verificationState: evidenceForm.verificationState,
                          notes: evidenceForm.notes || undefined,
                        });
                      }}
                    >
                      <select value={selectedRegistrationId} onChange={(event) => setSelectedRegistrationId(event.target.value)} className={inputClass}>
                        <option value="">Latest pending registration</option>
                        {profile.registrations.map((registration) => (
                          <option key={registration.registrationId} value={registration.registrationId}>
                            {labelize(registration.registrationType)} ({labelize(registration.workflowState)})
                          </option>
                        ))}
                      </select>
                      <input value={evidenceForm.evidenceType} onChange={(event) => setEvidenceForm((current) => ({ ...current, evidenceType: event.target.value }))} className={inputClass} placeholder="Evidence type" />
                      <input value={evidenceForm.evidenceReference} onChange={(event) => setEvidenceForm((current) => ({ ...current, evidenceReference: event.target.value }))} className={inputClass} placeholder="File reference, URI, or verification token" />
                      <select value={evidenceForm.verificationState} onChange={(event) => setEvidenceForm((current) => ({ ...current, verificationState: event.target.value }))} className={inputClass}>
                        <option value="SELF_ASSERTED">Self asserted</option>
                        <option value="PROVIDER_CAPTURED">Provider captured</option>
                        <option value="PARTIALLY_VERIFIED">Partially verified</option>
                        <option value="VERIFIED">Verified</option>
                        <option value="REVIEW_REQUIRED">Review required</option>
                      </select>
                      <textarea value={evidenceForm.notes} onChange={(event) => setEvidenceForm((current) => ({ ...current, notes: event.target.value }))} rows={3} className={inputClass} placeholder="Evidence notes" />
                      <button type="submit" className="rounded-xl bg-neutral-900 px-4 py-2.5 text-sm font-medium text-white hover:bg-slate-700">
                        Attach evidence
                      </button>
                    </form>
                  </div>

                  <div className="rounded-2xl border border-border bg-card p-5">
                    <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Verification decision</h3>
                    <form
                      className="mt-4 space-y-3"
                      onSubmit={(event) => {
                        event.preventDefault();
                        recordVerificationReview.mutate({
                          registrationId: selectedRegistrationId || pendingRegistrations[0]?.registrationId,
                          reviewType: verificationForm.reviewType,
                          decision: verificationForm.decision,
                          notes: verificationForm.notes || undefined,
                          identityAssuranceLevel: Number(verificationForm.identityAssuranceLevel),
                          issueImpiloId: verificationForm.issueImpiloId,
                        });
                      }}
                    >
                      <input value={verificationForm.reviewType} onChange={(event) => setVerificationForm((current) => ({ ...current, reviewType: event.target.value }))} className={inputClass} placeholder="Review type" />
                      <select value={verificationForm.decision} onChange={(event) => setVerificationForm((current) => ({ ...current, decision: event.target.value }))} className={inputClass}>
                        <option value="VERIFIED">Verified</option>
                        <option value="APPROVED">Approved</option>
                        <option value="REVIEW_REQUIRED">Review required</option>
                      </select>
                      <input value={verificationForm.identityAssuranceLevel} onChange={(event) => setVerificationForm((current) => ({ ...current, identityAssuranceLevel: event.target.value }))} className={inputClass} placeholder="Identity assurance level" />
                      <textarea value={verificationForm.notes} onChange={(event) => setVerificationForm((current) => ({ ...current, notes: event.target.value }))} rows={3} className={inputClass} placeholder="Verification notes" />
                      <label className="flex items-center gap-3 rounded-xl border border-border bg-background px-4 py-3 text-sm text-foreground">
                        <input
                          type="checkbox"
                          checked={verificationForm.issueImpiloId}
                          onChange={(event) => setVerificationForm((current) => ({ ...current, issueImpiloId: event.target.checked }))}
                          className="h-4 w-4 rounded border-border"
                        />
                        Issue or confirm the canonical Impilo ID on approval.
                      </label>
                      <button type="submit" className="rounded-xl bg-neutral-900 px-4 py-2.5 text-sm font-medium text-white hover:bg-slate-700">
                        Record decision
                      </button>
                    </form>
                  </div>
                </div>
              </div>
            ) : null}

            {activeTab === "matching" ? (
              <div className="grid gap-4 lg:grid-cols-[1.1fr_0.9fr]">
                <div className="rounded-2xl border border-border bg-card p-5">
                  <div className="flex items-center justify-between gap-3">
                    <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Duplicate candidates</h3>
                    <button type="button" onClick={() => runMatching.mutate()} className="rounded-xl border border-border px-4 py-2 text-sm font-medium text-foreground hover:border-slate-400 hover:text-foreground">
                      Run matching
                    </button>
                  </div>
                  <div className="mt-4 space-y-3">
                    {profile.matchCandidates.map((match) => (
                      <div key={match.matchId} className="rounded-2xl border border-border p-4">
                        <div className="flex flex-wrap items-start justify-between gap-3">
                          <div>
                            <p className="font-medium text-foreground">Candidate {match.candidateHealthId.slice(0, 8)}</p>
                            <p className="mt-1 text-xs text-muted-foreground">
                              Score {match.matchScore} • {match.matchReasonSummary}
                            </p>
                          </div>
                          <span className={`rounded-full border px-2.5 py-1 text-xs font-semibold ${toneForStatus(match.status)}`}>
                            {labelize(match.status)}
                          </span>
                        </div>
                        <div className="mt-3 flex flex-wrap gap-2">
                          <button type="button" onClick={() => reviewMatchCandidate.mutate({ matchId: match.matchId, body: { outcome: "CONFIRMED_DISTINCT" } })} className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:border-slate-400 hover:text-foreground">
                            Mark distinct
                          </button>
                          <button type="button" onClick={() => reviewMatchCandidate.mutate({ matchId: match.matchId, body: { outcome: "CONFIRMED_DUPLICATE" } })} className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:border-slate-400 hover:text-foreground">
                            Confirm duplicate
                          </button>
                          <button type="button" onClick={() => setSelectedMatchId(String(match.matchId))} className="rounded-lg border border-slate-900 bg-neutral-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-700">
                            Prepare merge
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="space-y-4">
                  <div className="rounded-2xl border border-border bg-card p-5">
                    <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Open merge cases</h3>
                    <div className="mt-4 space-y-3">
                      {profile.mergeCases.map((mergeCase) => (
                        <div key={mergeCase.mergeCaseId} className="rounded-2xl bg-background p-4">
                          <p className="font-medium text-foreground">
                            {mergeCase.primaryHealthId.slice(0, 8)} absorbs {mergeCase.secondaryHealthId.slice(0, 8)}
                          </p>
                          <p className="mt-1 text-xs text-muted-foreground">{labelize(mergeCase.status)}</p>
                          {mergeCase.status === "OPEN" ? (
                            <div className="mt-3 flex gap-2">
                              <button type="button" onClick={() => decideMergeCase.mutate({ mergeCaseId: mergeCase.mergeCaseId, body: { decision: "APPROVED", survivorshipSummary: mergeCase.survivorshipSummary ?? "Keep primary identifiers and latest contact channels." } })} className="rounded-lg border border-slate-900 bg-neutral-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-700">
                                Approve merge
                              </button>
                              <button type="button" onClick={() => setSelectedMergeCaseId(mergeCase.mergeCaseId)} className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:border-slate-400 hover:text-foreground">
                                Focus case
                              </button>
                            </div>
                          ) : null}
                        </div>
                      ))}
                    </div>
                  </div>

                  <div className="rounded-2xl border border-border bg-card p-5">
                    <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Create merge case</h3>
                    <form
                      className="mt-4 space-y-3"
                      onSubmit={(event) => {
                        event.preventDefault();
                        if (!selectedMatchId) return;
                        const selectedMatch = profile.matchCandidates.find((match) => String(match.matchId) === selectedMatchId);
                        if (!selectedMatch) return;
                        createMergeCase.mutate({
                          primaryHealthId: master.healthId,
                          secondaryHealthId: selectedMatch.candidateHealthId,
                          linkedMatchId: Number(selectedMatchId),
                          survivorshipSummary: "Preserve primary record as golden record; retain secondary provenance and history.",
                        });
                      }}
                    >
                      <select value={selectedMatchId} onChange={(event) => setSelectedMatchId(event.target.value)} className={inputClass}>
                        <option value="">Choose a confirmed duplicate candidate</option>
                        {profile.matchCandidates.map((match) => (
                          <option key={match.matchId} value={match.matchId}>
                            {match.candidateHealthId} ({labelize(match.status)})
                          </option>
                        ))}
                      </select>
                      <p className="text-xs text-muted-foreground">
                        Selected merge case: {selectedMergeCaseId ? selectedMergeCaseId.slice(0, 8) : "none"}
                      </p>
                      <button type="submit" className="rounded-xl bg-neutral-900 px-4 py-2.5 text-sm font-medium text-white hover:bg-slate-700">
                        Open merge review
                      </button>
                    </form>
                  </div>
                </div>
              </div>
            ) : null}

            {activeTab === "relationships" ? (
              <div className="grid gap-4 lg:grid-cols-[1.1fr_0.9fr]">
                <div className="rounded-2xl border border-border bg-card p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Relationship graph</h3>
                  <div className="mt-4 space-y-3">
                    {profile.relationships.map((relationship) => (
                      <div key={relationship.relationshipId} className="rounded-2xl border border-border p-4">
                        <p className="font-medium text-foreground">{labelize(relationship.relationshipType)}</p>
                        <p className="mt-1 text-xs text-muted-foreground">
                          Related client {relationship.relatedClientHealthId.slice(0, 8)} • {labelize(relationship.status)}
                        </p>
                        <p className="mt-2 text-sm text-muted-foreground">{relationship.notes ?? "No notes recorded."}</p>
                      </div>
                    ))}
                  </div>
                </div>
                <div className="rounded-2xl border border-border bg-card p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Create relationship</h3>
                  <form
                    className="mt-4 space-y-3"
                    onSubmit={(event) => {
                      event.preventDefault();
                      addRelationship.mutate(relationshipForm);
                    }}
                  >
                    <input value={relationshipForm.relatedClientHealthId} onChange={(event) => setRelationshipForm((current) => ({ ...current, relatedClientHealthId: event.target.value }))} className={inputClass} placeholder="Related client health ID" />
                    <select value={relationshipForm.relationshipType} onChange={(event) => setRelationshipForm((current) => ({ ...current, relationshipType: event.target.value }))} className={inputClass}>
                      <option value="GUARDIAN_OF">Guardian of</option>
                      <option value="DEPENDENT_OF">Dependent of</option>
                      <option value="CAREGIVER_OF">Caregiver of</option>
                      <option value="NEXT_OF_KIN">Next of kin</option>
                      <option value="PROXY_ACCESS_FOR">Proxy access for</option>
                    </select>
                    <textarea value={relationshipForm.notes} onChange={(event) => setRelationshipForm((current) => ({ ...current, notes: event.target.value }))} rows={3} className={inputClass} placeholder="Relationship provenance or notes" />
                    <button type="submit" className="rounded-xl bg-neutral-900 px-4 py-2.5 text-sm font-medium text-white hover:bg-slate-700">
                      Add relationship
                    </button>
                  </form>
                </div>
              </div>
            ) : null}

            {activeTab === "authorisation" ? (
              <div className="grid gap-4 lg:grid-cols-[1.1fr_0.9fr]">
                <div className="rounded-2xl border border-border bg-card p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Tshepo linkage references</h3>
                  <div className="mt-4 space-y-3">
                    {profile.authorizationLinks.map((link) => (
                      <div key={link.authorizationLinkId} className="rounded-2xl border border-border p-4">
                        <p className="font-medium text-foreground">{link.authorisationType}</p>
                        <p className="mt-1 text-sm text-foreground">{link.referenceId}</p>
                        <p className="mt-1 text-xs text-muted-foreground">{labelize(link.status)}</p>
                      </div>
                    ))}
                  </div>
                </div>
                <div className="rounded-2xl border border-border bg-card p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Create authorisation link</h3>
                  <form
                    className="mt-4 space-y-3"
                    onSubmit={(event) => {
                      event.preventDefault();
                      addAuthorizationLink.mutate(authorisationForm);
                    }}
                  >
                    <input value={authorisationForm.authorisationType} onChange={(event) => setAuthorisationForm((current) => ({ ...current, authorisationType: event.target.value }))} className={inputClass} placeholder="Authorisation type" />
                    <input value={authorisationForm.referenceId} onChange={(event) => setAuthorisationForm((current) => ({ ...current, referenceId: event.target.value }))} className={inputClass} placeholder="Reference ID from Tshepo or linked journey" />
                    <input value={authorisationForm.status} onChange={(event) => setAuthorisationForm((current) => ({ ...current, status: event.target.value }))} className={inputClass} placeholder="Status" />
                    <button type="submit" className="rounded-xl bg-neutral-900 px-4 py-2.5 text-sm font-medium text-white hover:bg-slate-700">
                      Link authorisation
                    </button>
                  </form>
                </div>
              </div>
            ) : null}

            {activeTab === "stewardship" ? (
              <div className="grid gap-4 lg:grid-cols-[1.1fr_0.9fr]">
                <div className="rounded-2xl border border-border bg-card p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Stewardship queue</h3>
                  <div className="mt-4 space-y-3">
                    {profile.stewardshipActions.map((action) => (
                      <div key={action.actionId} className="rounded-2xl border border-border p-4">
                        <div className="flex flex-wrap items-start justify-between gap-3">
                          <div>
                            <p className="font-medium text-foreground">{labelize(action.actionType)}</p>
                            <p className="mt-1 text-xs text-muted-foreground">
                              {labelize(action.status)} • Due {action.dueDate ? new Date(action.dueDate).toLocaleDateString() : "not set"}
                            </p>
                          </div>
                          {action.status !== "VERIFIED" ? (
                            <button
                              type="button"
                              onClick={() => updateStewardshipAction.mutate({ actionId: action.actionId, body: { status: "COMPLETED", completionNotes: "Completed from workspace." } })}
                              className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:border-slate-400 hover:text-foreground"
                            >
                              Complete
                            </button>
                          ) : null}
                        </div>
                        <p className="mt-2 text-sm text-muted-foreground">{action.completionNotes ?? "No notes recorded."}</p>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="rounded-2xl border border-border bg-card p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Create stewardship action</h3>
                  <form
                    className="mt-4 space-y-3"
                    onSubmit={(event) => {
                      event.preventDefault();
                      createStewardshipAction.mutate({
                        actionType: stewardshipForm.actionType,
                        owner: stewardshipForm.owner || undefined,
                        dueDate: stewardshipForm.dueDate ? new Date(stewardshipForm.dueDate).toISOString() : undefined,
                        completionNotes: stewardshipForm.completionNotes || undefined,
                      });
                    }}
                  >
                    <select value={stewardshipForm.actionType} onChange={(event) => setStewardshipForm((current) => ({ ...current, actionType: event.target.value }))} className={inputClass}>
                      <option value="VERIFY_IDENTITY">Verify identity</option>
                      <option value="REVIEW_DUPLICATE">Review duplicate</option>
                      <option value="COMPLETE_DEMOGRAPHICS">Complete demographics</option>
                      <option value="LINK_GUARDIAN">Link guardian</option>
                      <option value="AUTHORISATION_REVIEW">Authorisation review</option>
                      <option value="MERGE_REVIEW">Merge review</option>
                      <option value="CORRECT_RECORD">Correct record</option>
                    </select>
                    <input value={stewardshipForm.owner} onChange={(event) => setStewardshipForm((current) => ({ ...current, owner: event.target.value }))} className={inputClass} placeholder="Owner" />
                    <input type="datetime-local" value={stewardshipForm.dueDate} onChange={(event) => setStewardshipForm((current) => ({ ...current, dueDate: event.target.value }))} className={inputClass} />
                    <textarea value={stewardshipForm.completionNotes} onChange={(event) => setStewardshipForm((current) => ({ ...current, completionNotes: event.target.value }))} rows={3} className={inputClass} placeholder="Initial notes" />
                    <button type="submit" className="rounded-xl bg-neutral-900 px-4 py-2.5 text-sm font-medium text-white hover:bg-slate-700">
                      Create action
                    </button>
                  </form>
                </div>
              </div>
            ) : null}

            {activeTab === "audit" ? (
              <div className="grid gap-4 lg:grid-cols-2">
                <div className="rounded-2xl border border-border bg-card p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Status history</h3>
                  <div className="mt-4 space-y-3">
                    {profile.statusHistory.map((entry) => (
                      <div key={entry.statusHistoryId} className="rounded-2xl bg-background p-4">
                        <div className="flex items-center gap-2 text-sm font-medium text-foreground">
                          <BadgeCheck className="h-4 w-4 text-primary" />
                          {labelize(entry.previousStatus)} → {labelize(entry.newStatus)}
                        </div>
                        <p className="mt-1 text-xs text-muted-foreground">
                          {entry.changedBy} • {new Date(entry.changedAt).toLocaleString()}
                        </p>
                        <p className="mt-2 text-sm text-muted-foreground">{entry.reason ?? "No reason recorded."}</p>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="rounded-2xl border border-border bg-card p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Audit trail</h3>
                  <div className="mt-4 space-y-3">
                    {profile.auditTrail.map((event) => (
                      <div key={event.auditEventId} className="rounded-2xl border border-border p-4">
                        <div className="flex items-center gap-2 text-sm font-medium text-foreground">
                          {event.action.includes("MERGE") ? <GitMerge className="h-4 w-4 text-orange-600" /> : null}
                          {event.action.includes("VERIFICATION") ? <ShieldCheck className="h-4 w-4 text-primary" /> : null}
                          {event.action.includes("RELATIONSHIP") ? <Link2 className="h-4 w-4 text-sky-600" /> : null}
                          {!event.action.includes("MERGE") && !event.action.includes("VERIFICATION") && !event.action.includes("RELATIONSHIP") ? (
                            <UserCog className="h-4 w-4 text-muted-foreground" />
                          ) : null}
                          {labelize(event.action)}
                        </div>
                        <p className="mt-1 text-xs text-muted-foreground">
                          {event.actor} • {new Date(event.createdAt).toLocaleString()}
                        </p>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            ) : null}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
