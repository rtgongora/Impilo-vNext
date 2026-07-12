"use client";

/**
 * HPA facility regulatory file.
 * Route: /registry/facility-lifecycle/[facilityId] (REGULATORY_AUTHORITY)
 *
 * One governed file per facility: applications (create → documents → submit →
 * ready-for-inspection), inspections (schedule with checklist template → record
 * outcome with findings), committee decisions (with certificate issuance),
 * certificates, practitioner-in-charge, enforcement and the status-history chain.
 * Every action here drives the tuso FacilityRegulatoryService — no local state.
 */

import { useMemo, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft,
  Award,
  Building2,
  FileText,
  Gavel,
  Landmark,
  Loader2,
  MailQuestion,
  ShieldAlert,
  ShoppingBag,
  Stethoscope,
  UserCheck,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useFacilityProfile,
  useSubmitFacilityApplication,
  useMarkApplicationReadyForInspection,
  useUploadFacilityDocument,
  useScheduleFacilityInspection,
  useRecordFacilityInspection,
  useRecordCommitteeDecision,
  useOpenEnforcementCase,
  useChecklistTemplates,
  type FacilityProfile,
  type FacilityApplicationView,
  type FacilityInspectionView,
} from "@/hooks/queries/useFacilityRegulatory";
import {
  useFacilityPicNominations,
  useNominatePic,
  useRespondPicNomination,
  useReviewPicNomination,
  useActivatePicNomination,
  useWithdrawPicNomination,
  useInformationRequests,
  useOpenInformationRequest,
  useRespondInformationRequest,
  useCloseInformationRequest,
  useCouncilReviews,
  useRecordCouncilReview,
  usePremises,
  useCreatePremises,
  useAssignOccupancy,
  useOccupancyHistory,
  useInspectionVisits,
  useCreateInspectionVisit,
  useVisitResponses,
  useRecordVisitResponses,
  useCompleteVisit,
  useInspectionChecklist,
  useVisitProgress,
  type PicNominationView,
  type InspectionVisitView,
  type ChecklistItemView,
  type ComposedChecklistModule,
} from "@/hooks/queries/useHpaRegulatory";
import { useResolveRequirementSourcing } from "@/hooks/queries/useRequirementSourcing";

function errMsg(e: unknown, fallback: string): string {
  return (
    (e as { body?: { error?: { message?: string } } })?.body?.error?.message ??
    (e as Error)?.message ??
    fallback
  );
}

function fmtDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  return iso.slice(0, 10);
}

export default function FacilityRegulatoryFilePage() {
  const params = useParams();
  const facilityId = Number(params.facilityId);
  const profileQuery = useFacilityProfile(Number.isFinite(facilityId) ? facilityId : undefined);
  const profile = profileQuery.data?.data;

  return (
    <AppLayout>
      <PageShell
        title={profile ? profile.master.name : "Facility regulatory file"}
        subtitle={
          profile
            ? `${profile.master.facilityCode} · ${profile.master.facilityType ?? "—"} · ${profile.master.province ?? "—"}`
            : "Loading the governed registry file"
        }
      >
        <div className="mb-4 flex items-center justify-between gap-3">
          <Link
            href="/registry/facility-lifecycle"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Lifecycle console
          </Link>
          {profile && (
            <span className="rounded-full bg-muted px-3 py-1 text-xs font-medium uppercase tracking-wide text-muted-foreground">
              {profile.master.regulatoryStatus.replace(/_/g, " ")}
            </span>
          )}
        </div>

        {profileQuery.isLoading && (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        )}
        {profileQuery.isError && (
          <p className="text-sm text-red-600">
            Regulatory file unavailable — the facility may not exist or the registry is unreachable.
          </p>
        )}

        {profile && (
          <div className="grid gap-6 lg:grid-cols-2">
            <ApplicationsPanel facilityId={facilityId} profile={profile} />
            <InspectionsPanel facilityId={facilityId} profile={profile} />
            <RequirementSourcingPanel profile={profile} />
            <CommitteePanel facilityId={facilityId} profile={profile} />
            <CertificatesPanel profile={profile} />
            <PractitionerPanel profile={profile} />
            <EnforcementPanel facilityId={facilityId} profile={profile} />
            <PicNominationsPanel facilityId={facilityId} />
            <InformationRequestsPanel profile={profile} />
            <CouncilReviewPanel profile={profile} />
            <PremisesPanel facilityId={facilityId} />
            <HistoryPanel profile={profile} />
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

function Panel({
  title,
  icon: Icon,
  children,
  wide,
}: {
  title: string;
  icon: React.ComponentType<{ className?: string }>;
  children: React.ReactNode;
  wide?: boolean;
}) {
  return (
    <section className={`rounded-lg border border-border bg-card p-4 ${wide ? "lg:col-span-2" : ""}`}>
      <h3 className="mb-3 flex items-center gap-2 text-sm font-medium text-foreground">
        <Icon className="h-4 w-4" /> {title}
      </h3>
      {children}
    </section>
  );
}

/* ─── Applications: documents → submit → ready-for-inspection ─── */

function ApplicationsPanel({ facilityId, profile }: { facilityId: number; profile: FacilityProfile }) {
  const submit = useSubmitFacilityApplication();
  const ready = useMarkApplicationReadyForInspection();
  const upload = useUploadFacilityDocument();
  const [docType, setDocType] = useState("PREMISES_PLAN");
  const [docRef, setDocRef] = useState("");
  const [error, setError] = useState<string | null>(null);

  const openApps = profile.applications.filter((a) => !a.finalDecision);

  return (
    <Panel title="Applications" icon={FileText}>
      {error && <p className="mb-2 text-xs text-red-600">{error}</p>}
      {openApps.length === 0 && (
        <p className="text-xs text-muted-foreground">
          No open applications. Create one from the lifecycle console.
        </p>
      )}
      <ul className="space-y-3">
        {openApps.map((a: FacilityApplicationView) => (
          <li key={a.applicationId} className="rounded-md border border-border/60 p-3">
            <div className="flex items-center justify-between gap-2">
              <div>
                <p className="text-sm font-medium text-foreground">{a.applicationType.replace(/_/g, " ")}</p>
                <p className="text-xs text-muted-foreground">
                  {a.applicantName} · {a.pathway ?? "STANDARD"} · state {a.currentWorkflowState}
                </p>
              </div>
              <div className="flex gap-2">
                {a.currentWorkflowState === "DRAFT" && (
                  <button
                    type="button"
                    disabled={submit.isPending}
                    onClick={() => {
                      setError(null);
                      submit.mutate(
                        { applicationId: a.applicationId, facilityId },
                        { onError: (e) => setError(errMsg(e, "Submission failed.")) },
                      );
                    }}
                    className="rounded-md bg-primary px-3 py-1 text-xs font-medium text-primary-foreground disabled:opacity-50"
                  >
                    Submit
                  </button>
                )}
                {a.currentWorkflowState === "SUBMITTED" && (
                  <button
                    type="button"
                    disabled={ready.isPending}
                    onClick={() => {
                      setError(null);
                      ready.mutate(
                        { applicationId: a.applicationId, facilityId },
                        { onError: (e) => setError(errMsg(e, "Could not mark ready for inspection.")) },
                      );
                    }}
                    className="rounded-md border border-border px-3 py-1 text-xs font-medium text-foreground disabled:opacity-50"
                  >
                    Ready for inspection
                  </button>
                )}
              </div>
            </div>

            {/* Documents on the application */}
            <div className="mt-2 border-t border-border/60 pt-2">
              <p className="mb-1 text-[11px] uppercase tracking-wide text-muted-foreground">Documents</p>
              <ul className="mb-2 space-y-0.5">
                {profile.documents.map((d) => (
                  <li key={d.documentId} className="flex items-center justify-between text-xs">
                    <span className="text-foreground">
                      {d.documentType.replace(/_/g, " ")} · v{d.version}
                    </span>
                    <span className="text-muted-foreground">{d.verificationState}</span>
                  </li>
                ))}
                {profile.documents.length === 0 && (
                  <li className="text-xs text-muted-foreground">No documents attached yet.</li>
                )}
              </ul>
              {a.currentWorkflowState === "DRAFT" && (
                <div className="flex flex-wrap gap-2">
                  <select
                    value={docType}
                    onChange={(e) => setDocType(e.target.value)}
                    className="rounded-md border border-border bg-background px-2 py-1 text-xs"
                  >
                    {["PREMISES_PLAN", "COUNCIL_CLEARANCE", "PROOF_OF_OWNERSHIP", "STAFF_LIST", "EQUIPMENT_LIST", "OTHER"].map(
                      (t) => (
                        <option key={t} value={t}>
                          {t.replace(/_/g, " ")}
                        </option>
                      ),
                    )}
                  </select>
                  <input
                    value={docRef}
                    onChange={(e) => setDocRef(e.target.value)}
                    placeholder="Document reference (file/registry ref)"
                    className="flex-1 rounded-md border border-border bg-background px-2 py-1 text-xs"
                  />
                  <button
                    type="button"
                    disabled={!docRef.trim() || upload.isPending}
                    onClick={() => {
                      setError(null);
                      upload.mutate(
                        {
                          facilityId,
                          applicationId: a.applicationId,
                          documentType: docType,
                          fileReference: docRef.trim(),
                        },
                        {
                          onSuccess: () => setDocRef(""),
                          onError: (e) => setError(errMsg(e, "Document could not be attached.")),
                        },
                      );
                    }}
                    className="rounded-md border border-border px-2 py-1 text-xs font-medium disabled:opacity-50"
                  >
                    Attach
                  </button>
                </div>
              )}
            </div>
          </li>
        ))}
      </ul>
    </Panel>
  );
}

/* ─── Inspections: schedule with checklist → record outcome ─── */

const INSPECTION_TYPES = ["INITIAL", "ROUTINE", "FOLLOW_UP", "COMPLAINT_TRIGGERED"];

function InspectionsPanel({ facilityId, profile }: { facilityId: number; profile: FacilityProfile }) {
  const schedule = useScheduleFacilityInspection();
  const record = useRecordFacilityInspection();
  const [inspectionType, setInspectionType] = useState("INITIAL");
  const templates = useChecklistTemplates(inspectionType, profile.master.facilityType);
  const [templateCode, setTemplateCode] = useState("");
  const [scheduledDate, setScheduledDate] = useState("");
  const [applicationId, setApplicationId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [recordingId, setRecordingId] = useState<string | null>(null);
  const [passed, setPassed] = useState(true);
  const [notes, setNotes] = useState("");

  const templateList = (templates.data?.data ?? []) as Array<{ templateCode?: string; name?: string }>;
  const openApps = profile.applications.filter((a) => !a.finalDecision);
  const openInspections = profile.inspections.filter((i) => i.status !== "COMPLETED");

  return (
    <Panel title="Inspections" icon={Stethoscope}>
      {error && <p className="mb-2 text-xs text-red-600">{error}</p>}

      <ul className="mb-3 space-y-2">
        {profile.inspections.map((i: FacilityInspectionView) => (
          <li key={i.inspectionId} className="rounded-md border border-border/60 p-3">
            <div className="flex items-center justify-between gap-2">
              <div>
                <p className="text-sm text-foreground">
                  {i.inspectionType.replace(/_/g, " ")}
                  {i.templateName ? ` · ${i.templateName}` : ""}
                </p>
                <p className="text-xs text-muted-foreground">
                  {i.status} · scheduled {fmtDate(i.scheduledDate)}
                  {i.outcome ? ` · outcome ${i.outcome}` : ""}
                </p>
              </div>
              {i.status !== "COMPLETED" && (
                <button
                  type="button"
                  onClick={() => setRecordingId(recordingId === i.inspectionId ? null : i.inspectionId)}
                  className="rounded-md border border-border px-3 py-1 text-xs font-medium"
                >
                  {recordingId === i.inspectionId ? "Close" : "Record outcome"}
                </button>
              )}
            </div>
            {recordingId === i.inspectionId && (
              <div className="mt-2 space-y-2 border-t border-border/60 pt-2">
                <label className="flex items-center gap-2 text-xs text-foreground">
                  <input type="checkbox" checked={passed} onChange={(e) => setPassed(e.target.checked)} />
                  All checklist requirements met (fail unchecked to raise a critical finding)
                </label>
                <textarea
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  placeholder="Inspector notes"
                  rows={2}
                  className="w-full rounded-md border border-border bg-background px-2 py-1 text-xs"
                />
                <button
                  type="button"
                  disabled={record.isPending}
                  onClick={() => {
                    setError(null);
                    record.mutate(
                      {
                        inspectionId: i.inspectionId,
                        facilityId,
                        body: {
                          actualDate: new Date().toISOString().slice(0, 10),
                          notes: notes || undefined,
                          findings: [
                            {
                              checklistItemCode: "GEN-001",
                              requirementText: "General compliance with the applicable checklist",
                              criticalFlag: !passed,
                              status: passed ? "COMPLIANT" : "NON_COMPLIANT",
                              severity: passed ? "LOW" : "CRITICAL",
                              comments: notes || undefined,
                            },
                          ],
                        },
                      },
                      {
                        onSuccess: () => {
                          setRecordingId(null);
                          setNotes("");
                        },
                        onError: (e) => setError(errMsg(e, "Outcome could not be recorded.")),
                      },
                    );
                  }}
                  className="rounded-md bg-primary px-3 py-1 text-xs font-medium text-primary-foreground disabled:opacity-50"
                >
                  {record.isPending ? "Recording…" : "Record inspection outcome"}
                </button>
              </div>
            )}
            <ChecklistSection inspectionId={i.inspectionId} />
            <VisitsSection inspectionId={i.inspectionId} />
          </li>
        ))}
        {profile.inspections.length === 0 && (
          <li className="text-xs text-muted-foreground">No inspections yet.</li>
        )}
      </ul>

      {/* Schedule */}
      <div className="border-t border-border/60 pt-3">
        <p className="mb-2 text-[11px] uppercase tracking-wide text-muted-foreground">Schedule inspection</p>
        <div className="grid gap-2 sm:grid-cols-2">
          <select
            value={inspectionType}
            onChange={(e) => {
              setInspectionType(e.target.value);
              setTemplateCode("");
            }}
            className="rounded-md border border-border bg-background px-2 py-1.5 text-xs"
          >
            {INSPECTION_TYPES.map((t) => (
              <option key={t} value={t}>
                {t.replace(/_/g, " ")}
              </option>
            ))}
          </select>
          <select
            value={templateCode}
            onChange={(e) => setTemplateCode(e.target.value)}
            className="rounded-md border border-border bg-background px-2 py-1.5 text-xs"
          >
            <option value="">Checklist template…</option>
            {templateList.map((t) => (
              <option key={String(t.templateCode)} value={String(t.templateCode)}>
                {String(t.name ?? t.templateCode)}
              </option>
            ))}
          </select>
          <input
            type="date"
            value={scheduledDate}
            onChange={(e) => setScheduledDate(e.target.value)}
            className="rounded-md border border-border bg-background px-2 py-1.5 text-xs"
          />
          <select
            value={applicationId}
            onChange={(e) => setApplicationId(e.target.value)}
            className="rounded-md border border-border bg-background px-2 py-1.5 text-xs"
          >
            <option value="">Link application (optional)…</option>
            {openApps.map((a) => (
              <option key={a.applicationId} value={a.applicationId}>
                {a.applicationType.replace(/_/g, " ")} · {a.currentWorkflowState}
              </option>
            ))}
          </select>
        </div>
        <button
          type="button"
          disabled={schedule.isPending}
          onClick={() => {
            setError(null);
            schedule.mutate(
              {
                facilityId,
                applicationId: applicationId || undefined,
                inspectionType,
                templateCode: templateCode || undefined,
                scheduledDate: scheduledDate || undefined,
              },
              {
                onSuccess: () => setScheduledDate(""),
                onError: (e) => setError(errMsg(e, "Inspection could not be scheduled.")),
              },
            );
          }}
          className="mt-2 rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground disabled:opacity-50"
        >
          {schedule.isPending ? "Scheduling…" : "Schedule inspection"}
        </button>
        {openInspections.length > 0 && (
          <p className="mt-1 text-[11px] text-muted-foreground">
            {openInspections.length} inspection(s) already open.
          </p>
        )}
      </div>
    </Panel>
  );
}

/* ─── Rectification sourcing: open findings → marketplace supplier categories ─── */

function RequirementSourcingPanel({ profile }: { profile: FacilityProfile }) {
  const openFindings = useMemo(
    () => profile.findings.filter((f) => f.rectificationStatus === "OPEN"),
    [profile.findings],
  );
  const codes = useMemo(
    () => [...new Set(openFindings.map((f) => f.checklistItemCode))],
    [openFindings],
  );
  const resolveQuery = useResolveRequirementSourcing(codes);
  const resolutions = resolveQuery.data?.data.resolutions ?? {};

  return (
    <Panel title="Rectification sourcing" icon={ShoppingBag}>
      <p className="mb-3 text-[11px] text-muted-foreground">
        Marketplace sellers in the matching category for each open finding — a sourcing aid, not an endorsement.
      </p>
      {openFindings.length === 0 && (
        <p className="text-xs text-muted-foreground">No open findings awaiting rectification.</p>
      )}
      {codes.length > 0 && resolveQuery.isLoading && (
        <p className="flex items-center gap-2 text-xs text-muted-foreground">
          <Loader2 className="h-3 w-3 animate-spin" /> Resolving supplier categories…
        </p>
      )}
      {codes.length > 0 && resolveQuery.isError && (
        <p className="text-xs text-red-600">Supplier categories are unavailable right now.</p>
      )}
      <ul className="space-y-2">
        {codes.map((code) => {
          const resolution = resolutions[code];
          return (
            <li key={code} className="rounded-md border border-border/60 p-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-mono text-[11px] text-foreground">{code}</span>
                {resolution ? (
                  <span className="flex flex-wrap items-center gap-2">
                    <span className="text-xs text-muted-foreground">{resolution.categoryLabel}</span>
                    <Link
                      href={`/marketplace/store?category=${encodeURIComponent(resolution.categoryCode)}`}
                      className="text-xs text-primary hover:underline"
                    >
                      {resolution.publishedListingCount} supplier
                      {resolution.publishedListingCount === 1 ? "" : "s"} on marketplace
                    </Link>
                    {resolution.restricted && (
                      <span className="text-[10px] text-amber-700">licensed suppliers only</span>
                    )}
                  </span>
                ) : (
                  !resolveQuery.isLoading &&
                  !resolveQuery.isError && (
                    <span className="text-[11px] text-muted-foreground">No marketplace category mapped</span>
                  )
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </Panel>
  );
}

/* ─── Committee: record decision (+ certificate issuance side-effect) ─── */

function CommitteePanel({ facilityId, profile }: { facilityId: number; profile: FacilityProfile }) {
  const decide = useRecordCommitteeDecision();
  const [applicationId, setApplicationId] = useState("");
  const [decision, setDecision] = useState("APPROVED");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);

  const reviewableApps = profile.applications.filter((a) => !a.finalDecision);

  return (
    <Panel title="Committee decisions" icon={Gavel}>
      {error && <p className="mb-2 text-xs text-red-600">{error}</p>}
      <ul className="mb-3 space-y-1">
        {profile.committeeReviews.map((r) => (
          <li key={r.reviewId} className="flex items-center justify-between text-xs">
            <span className="text-foreground">
              {r.committeeType.replace(/_/g, " ")} · {r.decision}
            </span>
            <span className="text-muted-foreground">{fmtDate(r.decidedAt)}</span>
          </li>
        ))}
        {profile.committeeReviews.length === 0 && (
          <li className="text-xs text-muted-foreground">No committee decisions recorded.</li>
        )}
      </ul>
      <div className="border-t border-border/60 pt-3">
        <div className="grid gap-2 sm:grid-cols-2">
          <select
            value={applicationId}
            onChange={(e) => setApplicationId(e.target.value)}
            className="rounded-md border border-border bg-background px-2 py-1.5 text-xs"
          >
            <option value="">Application…</option>
            {reviewableApps.map((a) => (
              <option key={a.applicationId} value={a.applicationId}>
                {a.applicationType.replace(/_/g, " ")} · {a.currentWorkflowState}
              </option>
            ))}
          </select>
          <select
            value={decision}
            onChange={(e) => setDecision(e.target.value)}
            className="rounded-md border border-border bg-background px-2 py-1.5 text-xs"
          >
            {["APPROVED", "DEFERRED", "REJECTED"].map((d) => (
              <option key={d} value={d}>
                {d}
              </option>
            ))}
          </select>
        </div>
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder="Resolution notes"
          rows={2}
          className="mt-2 w-full rounded-md border border-border bg-background px-2 py-1 text-xs"
        />
        <button
          type="button"
          disabled={!applicationId || decide.isPending}
          onClick={() => {
            setError(null);
            decide.mutate(
              {
                facilityId,
                applicationId,
                committeeType: "REGISTRATION_COMMITTEE",
                decision,
                notes: notes || undefined,
                issueCertificate: decision === "APPROVED",
                certificateType: decision === "APPROVED" ? "REGISTRATION" : undefined,
              },
              {
                onSuccess: () => {
                  setNotes("");
                  setApplicationId("");
                },
                onError: (e) => setError(errMsg(e, "Decision could not be recorded.")),
              },
            );
          }}
          className="mt-2 rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground disabled:opacity-50"
        >
          {decide.isPending ? "Recording…" : "Record decision"}
        </button>
        <p className="mt-1 text-[11px] text-muted-foreground">
          An approval issues the registration certificate under HPA authority.
        </p>
      </div>
    </Panel>
  );
}

/* ─── Certificates ─── */

function CertificatesPanel({ profile }: { profile: FacilityProfile }) {
  return (
    <Panel title="Certificates" icon={Award}>
      <ul className="space-y-2">
        {profile.certificates.map((c) => (
          <li key={c.certificateId} className="rounded-md border border-border/60 p-3">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-foreground">{c.certificateNumber}</span>
              <span
                className={`rounded-full px-2 py-0.5 text-[10px] uppercase tracking-wide ${
                  c.status === "ACTIVE" ? "bg-emerald-500/10 text-emerald-600" : "bg-muted text-muted-foreground"
                }`}
              >
                {c.status}
              </span>
            </div>
            <p className="mt-0.5 text-xs text-muted-foreground">
              {c.certificateType} · issued {fmtDate(c.issueDate)}
              {c.expiryDate ? ` · expires ${fmtDate(c.expiryDate)}` : ""} · {c.issuedUnderAuthority}
            </p>
          </li>
        ))}
        {profile.certificates.length === 0 && (
          <li className="text-xs text-muted-foreground">No certificates issued yet.</li>
        )}
      </ul>
    </Panel>
  );
}

/* ─── Practitioner in charge ─── */

function PractitionerPanel({ profile }: { profile: FacilityProfile }) {
  return (
    <Panel title="Practitioner in charge" icon={UserCheck}>
      <ul className="space-y-2">
        {profile.practitionerAssignments.map((p) => (
          <li key={p.assignmentId} className="flex items-center justify-between text-sm">
            <span className="text-foreground">
              {p.providerPublicId}
              <span className="ml-2 text-xs text-muted-foreground">{p.role}</span>
            </span>
            <span className="text-xs text-muted-foreground">
              {p.approvalState} · from {fmtDate(p.startDate)}
            </span>
          </li>
        ))}
        {profile.practitionerAssignments.length === 0 && (
          <li className="text-xs text-muted-foreground">
            No practitioner-in-charge on record — assignments sync from the provider registry
            (VARAPI) council verdicts.
          </li>
        )}
      </ul>
    </Panel>
  );
}

/* ─── Enforcement ─── */

function EnforcementPanel({ facilityId, profile }: { facilityId: number; profile: FacilityProfile }) {
  const open = useOpenEnforcementCase();
  const [triggerType, setTriggerType] = useState("FAILED_INSPECTION");
  const [details, setDetails] = useState("");
  const [error, setError] = useState<string | null>(null);

  return (
    <Panel title="Enforcement" icon={ShieldAlert}>
      {error && <p className="mb-2 text-xs text-red-600">{error}</p>}
      <ul className="mb-3 space-y-1">
        {profile.enforcementCases.map((c) => (
          <li key={c.caseId} className="flex items-center justify-between text-xs">
            <span className="text-foreground">{c.triggerType.replace(/_/g, " ")}</span>
            <span className="text-muted-foreground">
              {c.status} · opened {fmtDate(c.openedAt)}
            </span>
          </li>
        ))}
        {profile.enforcementCases.length === 0 && (
          <li className="text-xs text-muted-foreground">No enforcement cases.</li>
        )}
      </ul>
      <div className="border-t border-border/60 pt-3">
        <div className="flex flex-wrap gap-2">
          <select
            value={triggerType}
            onChange={(e) => setTriggerType(e.target.value)}
            className="rounded-md border border-border bg-background px-2 py-1.5 text-xs"
          >
            {["FAILED_INSPECTION", "COMPLAINT", "UNREGISTERED_OPERATION", "NON_COMPLIANCE"].map((t) => (
              <option key={t} value={t}>
                {t.replace(/_/g, " ")}
              </option>
            ))}
          </select>
          <input
            value={details}
            onChange={(e) => setDetails(e.target.value)}
            placeholder="Case details"
            className="flex-1 rounded-md border border-border bg-background px-2 py-1.5 text-xs"
          />
          <button
            type="button"
            disabled={open.isPending}
            onClick={() => {
              setError(null);
              open.mutate(
                { facilityId, triggerType, decisionDetails: details || undefined },
                {
                  onSuccess: () => setDetails(""),
                  onError: (e) => setError(errMsg(e, "Case could not be opened.")),
                },
              );
            }}
            className="rounded-md border border-red-300 px-3 py-1.5 text-xs font-medium text-red-600 disabled:opacity-50"
          >
            Open case
          </button>
        </div>
      </div>
    </Panel>
  );
}

/* ─── Status history (the audit chain) ─── */

function HistoryPanel({ profile }: { profile: FacilityProfile }) {
  return (
    <Panel title="Status history" icon={FileText} wide>
      <ol className="space-y-1">
        {profile.statusHistory.map((h, idx) => (
          <li key={`${h.changedAt}-${idx}`} className="flex items-center justify-between text-xs">
            <span className="text-foreground">
              {h.regulatoryStatus.replace(/_/g, " ")}
              {h.reason ? <span className="ml-2 text-muted-foreground">— {h.reason}</span> : null}
            </span>
            <span className="text-muted-foreground">
              {fmtDate(h.changedAt)} · {h.changedBy}
              {h.authorityContext ? ` · ${h.authorityContext}` : ""}
            </span>
          </li>
        ))}
        {profile.statusHistory.length === 0 && (
          <li className="text-xs text-muted-foreground">No status transitions recorded.</li>
        )}
      </ol>
    </Panel>
  );
}

/* ─── Composed inspection checklist (manual-derived catalogue, read-only) ─── */

function fmtManualPages(pages: number[] | null | undefined): string | null {
  if (!pages || pages.length === 0) return null;
  const first = pages[0];
  const last = pages[pages.length - 1];
  return first === last ? `Manual p. ${first}` : `Manual pp. ${first}–${last}`;
}

function obligationChipClass(obligation: string | null | undefined): string {
  switch (obligation) {
    case "MANDATORY":
      return "bg-red-500/10 text-red-600";
    case "RECOMMENDED":
      return "bg-sky-500/10 text-sky-600";
    case "WHERE_APPLICABLE":
    case "WHERE_POSSIBLE":
      return "bg-amber-500/10 text-amber-600";
    default:
      return "bg-muted text-muted-foreground";
  }
}

function groupItemsBySection(
  items: ChecklistItemView[],
): Array<{ section: string; items: ChecklistItemView[] }> {
  const groups: Array<{ section: string; items: ChecklistItemView[] }> = [];
  const index = new Map<string, ChecklistItemView[]>();
  for (const item of items) {
    const section = item.section ?? "General";
    let bucket = index.get(section);
    if (!bucket) {
      bucket = [];
      index.set(section, bucket);
      groups.push({ section, items: bucket });
    }
    bucket.push(item);
  }
  return groups;
}

function ChecklistSection({ inspectionId }: { inspectionId: string }) {
  const [expanded, setExpanded] = useState(false);
  const checklistQuery = useInspectionChecklist(expanded ? inspectionId : undefined);
  const checklist = checklistQuery.data?.data;

  return (
    <div className="mt-2 border-t border-border/60 pt-2">
      <div className="flex items-center justify-between gap-2">
        <p className="text-[11px] uppercase tracking-wide text-muted-foreground">Checklist</p>
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className="rounded-md border border-border px-2 py-0.5 text-[11px] font-medium"
        >
          {expanded ? "Hide checklist" : "Show checklist"}
        </button>
      </div>
      {expanded && checklistQuery.isLoading && (
        <p className="mt-1 text-[11px] text-muted-foreground">Loading checklist…</p>
      )}
      {expanded && checklistQuery.isError && (
        <p className="mt-1 text-xs text-red-600">
          Checklist unavailable — no template composition is mapped for this facility profile yet.
        </p>
      )}
      {expanded && checklist && (
        <div className="mt-1 max-h-96 overflow-y-auto rounded-md border border-border/60">
          {checklist.mode === "COMPOSED" && (
            <>
              {checklist.compositionCode && (
                <p className="border-b border-border/60 bg-muted/40 px-2 py-1 text-[11px] text-muted-foreground">
                  Composition {checklist.compositionCode}
                  {typeof checklist.totalItems === "number" ? ` · ${checklist.totalItems} items` : ""}
                </p>
              )}
              {(checklist.modules ?? []).map((m) => (
                <ChecklistModuleBlock key={`${m.code}-v${m.version}`} module={m} />
              ))}
            </>
          )}
          {checklist.mode === "TEMPLATE" && (
            <div className="py-1">
              <p className="px-2 py-1 text-[11px] text-muted-foreground">
                Legacy template checklist
                {checklist.templateCode ? ` · ${checklist.templateCode}` : ""}
                {checklist.templateVersion != null ? ` v${checklist.templateVersion}` : ""}
              </p>
              <ChecklistItems items={checklist.items ?? []} />
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function ChecklistModuleBlock({ module }: { module: ComposedChecklistModule }) {
  const pages = fmtManualPages(module.sourcePages);
  return (
    <div className="border-b border-border/60 last:border-b-0">
      <div className="flex flex-wrap items-center gap-x-1.5 gap-y-0.5 bg-muted/40 px-2 py-1.5">
        <span className="text-xs font-medium text-foreground">{module.title}</span>
        {module.scope && (
          <span
            className={`rounded-full px-2 py-0.5 text-[10px] uppercase tracking-wide ${
              module.scope === "COMMON" ? "bg-muted text-muted-foreground" : "bg-indigo-500/10 text-indigo-600"
            }`}
          >
            {module.scope.replace(/_/g, "-")}
          </span>
        )}
        <span className="text-[10px] text-muted-foreground">v{module.version}</span>
        {(module.sourceHeading || pages) && (
          <span className="ml-auto text-[10px] text-muted-foreground">
            {module.sourceHeading ?? ""}
            {module.sourceHeading && pages ? " · " : ""}
            {pages ?? ""}
          </span>
        )}
      </div>
      {module.notes && <p className="px-2 pt-1 text-[10px] italic text-muted-foreground">{module.notes}</p>}
      <ChecklistItems items={module.items ?? []} />
    </div>
  );
}

function ChecklistItems({ items }: { items: ChecklistItemView[] }) {
  if (items.length === 0) {
    return <p className="px-2 py-1 text-[11px] text-muted-foreground">No checklist items.</p>;
  }
  return (
    <div className="overflow-x-auto">
      <div className="min-w-[28rem]">
        {groupItemsBySection(items).map((group) => (
          <div key={group.section}>
            <p className="px-2 pt-1.5 text-[10px] font-medium uppercase tracking-wide text-muted-foreground">
              {group.section}
            </p>
            <ul className="space-y-1 px-2 pb-1.5">
              {group.items.map((item) => (
                <ChecklistItemRow key={item.code} item={item} />
              ))}
            </ul>
          </div>
        ))}
      </div>
    </div>
  );
}

function ChecklistItemRow({ item }: { item: ChecklistItemView }) {
  const isComponent = item.groupRole === "COMPONENT";
  return (
    <li className={`text-[11px] ${isComponent ? "pl-4" : ""}`}>
      <span className="mr-1.5 font-mono text-muted-foreground">{item.code}</span>
      {item.sourceItemNumber && (
        <span className="mr-1.5 text-muted-foreground">(manual item {item.sourceItemNumber})</span>
      )}
      <span className="text-foreground">{item.requirement}</span>
      {item.obligation && (
        <span
          className={`ml-1.5 rounded-full px-1.5 py-0 text-[9px] uppercase tracking-wide ${obligationChipClass(item.obligation)}`}
        >
          {item.obligation.replace(/_/g, " ")}
        </span>
      )}
      {item.reviewRequired && (
        <span
          title={item.reviewReason ?? undefined}
          className="ml-1.5 cursor-help rounded-full bg-amber-500/10 px-1.5 py-0 text-[9px] uppercase tracking-wide text-amber-600"
        >
          Review required
        </span>
      )}
      {item.measurement && (
        <span className="ml-1.5 text-muted-foreground">
          — {item.measurement.dimension ?? "measurement"}: {item.measurement.expected ?? "—"}
          {item.measurement.unit ? ` (${item.measurement.unit})` : ""}
        </span>
      )}
    </li>
  );
}

/* ─── Inspection visits (multi-visit lifecycle per inspection) ─── */

const VISIT_MODES = ["ONSITE", "DESKTOP", "VIRTUAL"];
const VISIT_RESPONSE_OPTIONS = [
  "COMPLIANT",
  "NON_COMPLIANT",
  "NOT_APPLICABLE",
  "NOT_OBSERVED",
  "UNABLE_TO_VERIFY",
];

function VisitsSection({ inspectionId }: { inspectionId: string }) {
  const visitsQuery = useInspectionVisits(inspectionId);
  const createVisit = useCreateInspectionVisit();
  const [mode, setMode] = useState("ONSITE");
  const [error, setError] = useState<string | null>(null);

  const visits = visitsQuery.data?.data ?? [];

  return (
    <div className="mt-2 border-t border-border/60 pt-2">
      <div className="flex items-center justify-between gap-2">
        <p className="text-[11px] uppercase tracking-wide text-muted-foreground">Visits</p>
        <div className="flex items-center gap-1">
          <select
            value={mode}
            onChange={(e) => setMode(e.target.value)}
            className="rounded-md border border-border bg-background px-2 py-1 text-xs"
          >
            {VISIT_MODES.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
          <button
            type="button"
            disabled={createVisit.isPending}
            onClick={() => {
              setError(null);
              createVisit.mutate(
                { inspectionId, mode },
                { onError: (e) => setError(errMsg(e, "Visit could not be created.")) },
              );
            }}
            className="rounded-md border border-border px-2 py-1 text-xs font-medium disabled:opacity-50"
          >
            {createVisit.isPending ? "Creating…" : "New visit"}
          </button>
        </div>
      </div>
      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
      <ul className="mt-1 space-y-2">
        {visits.map((v) => (
          <VisitRow key={v.visitId} visit={v} inspectionId={inspectionId} />
        ))}
        {!visitsQuery.isLoading && visits.length === 0 && (
          <li className="text-xs text-muted-foreground">No visits recorded for this inspection.</li>
        )}
      </ul>
    </div>
  );
}

function VisitRow({ visit, inspectionId }: { visit: InspectionVisitView; inspectionId: string }) {
  const [expanded, setExpanded] = useState(false);
  const responsesQuery = useVisitResponses(expanded ? visit.visitId : undefined);
  const progressQuery = useVisitProgress(visit.status !== "COMPLETED" ? visit.visitId : undefined);
  const recordResponses = useRecordVisitResponses();
  const completeVisit = useCompleteVisit();
  const [itemCode, setItemCode] = useState("");
  const [requirementText, setRequirementText] = useState("");
  const [response, setResponse] = useState("COMPLIANT");
  const [critical, setCritical] = useState(false);
  const [comments, setComments] = useState("");
  const [error, setError] = useState<string | null>(null);

  const responses = responsesQuery.data?.data ?? [];
  const completed = visit.status === "COMPLETED";
  const progress = progressQuery.data?.data;

  return (
    <li className="rounded-md border border-border/60 p-2">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs text-foreground">
          Visit #{visit.visitNumber} · {visit.mode} · {visit.status}
          {visit.scheduledDate ? ` · scheduled ${fmtDate(visit.scheduledDate)}` : ""}
          {visit.leadInspectorName ? ` · lead ${visit.leadInspectorName}` : ""}
        </p>
        {!completed && (
          <button
            type="button"
            onClick={() => setExpanded((v) => !v)}
            className="rounded-md border border-border px-2 py-0.5 text-[11px] font-medium"
          >
            {expanded ? "Close" : "Capture responses"}
          </button>
        )}
      </div>
      {!completed && progress && (
        <div className="mt-1 rounded-md bg-muted/40 px-2 py-1 text-[11px]">
          <span className="text-foreground">
            Checklist progress: {progress.answered}/{progress.totalItems} answered
          </span>
          <span className={`ml-2 ${progress.outstandingMandatory.length > 0 ? "text-amber-600" : "text-emerald-600"}`}>
            {progress.outstandingMandatory.length} mandatory outstanding
          </span>
          <span className={`ml-2 ${progress.missingRequiredEvidence.length > 0 ? "text-red-600" : "text-emerald-600"}`}>
            {progress.missingRequiredEvidence.length} missing evidence
          </span>
          {progress.missingRequiredEvidence.length > 0 && (
            <p className="mt-0.5 overflow-x-auto whitespace-nowrap font-mono text-[10px] text-muted-foreground">
              Missing evidence: {progress.missingRequiredEvidence.map((m) => m.code).join(", ")}
            </p>
          )}
        </div>
      )}
      {expanded && !completed && (
        <div className="mt-2 space-y-2 border-t border-border/60 pt-2">
          {error && <p className="text-xs text-red-600">{error}</p>}
          <ul className="space-y-0.5">
            {responses.map((r, idx) => (
              <li key={r.responseId ?? `${r.itemCode}-${idx}`} className="flex items-center justify-between text-[11px]">
                <span className="text-foreground">
                  {r.itemCode}
                  {r.critical ? <span className="ml-1 text-red-600">critical</span> : null}
                  {r.comments ? <span className="ml-1 text-muted-foreground">— {r.comments}</span> : null}
                </span>
                <span
                  className={
                    r.response === "COMPLIANT"
                      ? "text-emerald-600"
                      : r.response === "NON_COMPLIANT"
                        ? "text-red-600"
                        : "text-muted-foreground"
                  }
                >
                  {r.response.replace(/_/g, " ")}
                </span>
              </li>
            ))}
            {responses.length === 0 && (
              <li className="text-[11px] text-muted-foreground">No item responses recorded yet.</li>
            )}
          </ul>
          {/* Manual item entry — checklist template items are not exposed via the profile */}
          <div className="grid gap-1 sm:grid-cols-2">
            <input
              value={itemCode}
              onChange={(e) => setItemCode(e.target.value)}
              placeholder="Item code (e.g. GEN-001)"
              className="rounded-md border border-border bg-background px-2 py-1 text-xs"
            />
            <select
              value={response}
              onChange={(e) => setResponse(e.target.value)}
              className="rounded-md border border-border bg-background px-2 py-1 text-xs"
            >
              {VISIT_RESPONSE_OPTIONS.map((o) => (
                <option key={o} value={o}>
                  {o.replace(/_/g, " ")}
                </option>
              ))}
            </select>
            <input
              value={requirementText}
              onChange={(e) => setRequirementText(e.target.value)}
              placeholder="Requirement text"
              className="rounded-md border border-border bg-background px-2 py-1 text-xs sm:col-span-2"
            />
            <input
              value={comments}
              onChange={(e) => setComments(e.target.value)}
              placeholder="Comments"
              className="rounded-md border border-border bg-background px-2 py-1 text-xs sm:col-span-2"
            />
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <label className="flex items-center gap-1 text-[11px] text-foreground">
              <input type="checkbox" checked={critical} onChange={(e) => setCritical(e.target.checked)} />
              Critical
            </label>
            <button
              type="button"
              disabled={!itemCode.trim() || recordResponses.isPending}
              onClick={() => {
                setError(null);
                recordResponses.mutate(
                  {
                    visitId: visit.visitId,
                    inspectionId,
                    items: [
                      {
                        itemCode: itemCode.trim(),
                        requirementText: requirementText.trim() || undefined,
                        response,
                        critical,
                        comments: comments.trim() || undefined,
                      },
                    ],
                  },
                  {
                    onSuccess: () => {
                      setItemCode("");
                      setRequirementText("");
                      setComments("");
                      setCritical(false);
                    },
                    onError: (e) => setError(errMsg(e, "Response could not be recorded.")),
                  },
                );
              }}
              className="rounded-md border border-border px-2 py-1 text-xs font-medium disabled:opacity-50"
            >
              {recordResponses.isPending ? "Recording…" : "Record"}
            </button>
            <button
              type="button"
              disabled={completeVisit.isPending}
              onClick={() => {
                setError(null);
                completeVisit.mutate(
                  { visitId: visit.visitId, inspectionId },
                  {
                    onSuccess: () => setExpanded(false),
                    onError: (e) => setError(errMsg(e, "Visit could not be completed.")),
                  },
                );
              }}
              className="rounded-md bg-primary px-2 py-1 text-xs font-medium text-primary-foreground disabled:opacity-50"
            >
              {completeVisit.isPending ? "Completing…" : "Complete visit"}
            </button>
          </div>
        </div>
      )}
    </li>
  );
}

/* ─── Practitioner-in-charge nominations (governed eligibility + acceptance + review) ─── */

function eligibilityChipClass(result: string | null | undefined): string {
  switch (result) {
    case "ELIGIBLE":
      return "bg-emerald-500/10 text-emerald-600";
    case "CONDITIONAL":
      return "bg-amber-500/10 text-amber-600";
    case "INELIGIBLE":
      return "bg-red-500/10 text-red-600";
    default:
      return "bg-muted text-muted-foreground";
  }
}

function axisStatusClass(status: string): string {
  switch (status) {
    case "PASS":
      return "text-emerald-600";
    case "WARN":
      return "text-amber-600";
    case "FAIL":
      return "text-red-600";
    default:
      return "text-muted-foreground";
  }
}

function PicNominationsPanel({ facilityId }: { facilityId: number }) {
  const nominationsQuery = useFacilityPicNominations(facilityId);
  const nominate = useNominatePic();
  const [providerPublicId, setProviderPublicId] = useState("");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);

  const nominations = nominationsQuery.data?.data ?? [];

  return (
    <Panel title="Practitioner-in-charge nominations" icon={UserCheck} wide>
      {error && <p className="mb-2 text-xs text-red-600">{error}</p>}
      <ul className="mb-3 space-y-2">
        {nominations.map((n) => (
          <PicNominationCard key={n.nominationId} nomination={n} facilityId={facilityId} />
        ))}
        {!nominationsQuery.isLoading && nominations.length === 0 && (
          <li className="text-xs text-muted-foreground">
            No PIC nominations yet — nominate a registered practitioner below.
          </li>
        )}
      </ul>
      <div className="border-t border-border/60 pt-3">
        <p className="mb-2 text-[11px] uppercase tracking-wide text-muted-foreground">Nominate practitioner in charge</p>
        <div className="flex flex-wrap gap-2">
          <input
            value={providerPublicId}
            onChange={(e) => setProviderPublicId(e.target.value)}
            placeholder="Provider public ID"
            className="flex-1 rounded-md border border-border bg-background px-2 py-1.5 text-xs"
          />
          <input
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Notes (optional)"
            className="flex-1 rounded-md border border-border bg-background px-2 py-1.5 text-xs"
          />
          <button
            type="button"
            disabled={!providerPublicId.trim() || nominate.isPending}
            onClick={() => {
              setError(null);
              nominate.mutate(
                { facilityId, providerPublicId: providerPublicId.trim(), notes: notes.trim() || undefined },
                {
                  onSuccess: () => {
                    setProviderPublicId("");
                    setNotes("");
                  },
                  onError: (e) => setError(errMsg(e, "Nomination failed — check the provider ID.")),
                },
              );
            }}
            className="rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground disabled:opacity-50"
          >
            {nominate.isPending ? "Nominating…" : "Nominate"}
          </button>
        </div>
        <p className="mt-1 text-[11px] text-muted-foreground">
          Eligibility is checked live against the provider registry (identity, council, registration,
          practising certificate, licence, restrictions).
        </p>
      </div>
    </Panel>
  );
}

function PicNominationCard({ nomination, facilityId }: { nomination: PicNominationView; facilityId: number }) {
  const respond = useRespondPicNomination();
  const review = useReviewPicNomination();
  const activate = useActivatePicNomination();
  const withdraw = useWithdrawPicNomination();
  const [authority, setAuthority] = useState("");
  const [error, setError] = useState<string | null>(null);

  const snapshot = nomination.eligibilitySnapshot;
  const mutationCtx = { facilityId, providerPublicId: nomination.providerPublicId };
  const terminal = ["ACTIVATED", "WITHDRAWN", "DECLINED", "REJECTED"].includes(nomination.state);

  return (
    <li className="rounded-md border border-border/60 p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="text-sm font-medium text-foreground">
            {snapshot?.displayName ?? nomination.providerPublicId}
            <span className="ml-2 text-xs font-normal text-muted-foreground">{nomination.providerPublicId}</span>
          </p>
          <p className="text-xs text-muted-foreground">
            {snapshot?.profession ?? "—"}
            {snapshot?.cadre ? ` · ${snapshot.cadre}` : ""}
            {snapshot?.councilCode ? ` · ${snapshot.councilCode}` : ""}
            {nomination.nominatedAt ? ` · nominated ${fmtDate(nomination.nominatedAt)}` : ""}
          </p>
        </div>
        <div className="flex items-center gap-1.5">
          <span className={`rounded-full px-2 py-0.5 text-[10px] uppercase tracking-wide ${eligibilityChipClass(nomination.eligibilityResult)}`}>
            {nomination.eligibilityResult ?? "UNKNOWN"}
          </span>
          <span className="rounded-full bg-muted px-2 py-0.5 text-[10px] uppercase tracking-wide text-muted-foreground">
            {nomination.state.replace(/_/g, " ")}
          </span>
        </div>
      </div>

      {snapshot && (
        <div className="mt-2 grid gap-x-4 gap-y-0.5 sm:grid-cols-2">
          {Object.entries(snapshot.axes ?? {}).map(([axis, verdict]) => (
            <div key={axis} className="flex items-center justify-between text-[11px]">
              <span className="text-muted-foreground">{axis.replace(/([A-Z])/g, " $1").toLowerCase()}</span>
              <span className={`font-medium ${axisStatusClass(verdict.status)}`} title={verdict.evidence ?? undefined}>
                {verdict.status}
              </span>
            </div>
          ))}
        </div>
      )}
      {snapshot && snapshot.reasons.length > 0 && (
        <ul className="mt-1 list-inside list-disc text-[11px] text-amber-700">
          {snapshot.reasons.map((r, idx) => (
            <li key={idx}>{r}</li>
          ))}
        </ul>
      )}

      {error && <p className="mt-2 text-xs text-red-600">{error}</p>}

      <div className="mt-2 flex flex-wrap items-center gap-2 border-t border-border/60 pt-2">
        {nomination.state === "PRACTITIONER_ACCEPTANCE_PENDING" && (
          <>
            <button
              type="button"
              disabled={respond.isPending}
              onClick={() => {
                setError(null);
                respond.mutate(
                  { nominationId: nomination.nominationId, response: "ACCEPTED", ...mutationCtx },
                  { onError: (e) => setError(errMsg(e, "Response could not be recorded.")) },
                );
              }}
              className="rounded-md border border-emerald-300 px-2 py-1 text-xs font-medium text-emerald-700 disabled:opacity-50"
            >
              Record acceptance
            </button>
            <button
              type="button"
              disabled={respond.isPending}
              onClick={() => {
                setError(null);
                respond.mutate(
                  { nominationId: nomination.nominationId, response: "DECLINED", ...mutationCtx },
                  { onError: (e) => setError(errMsg(e, "Response could not be recorded.")) },
                );
              }}
              className="rounded-md border border-red-300 px-2 py-1 text-xs font-medium text-red-600 disabled:opacity-50"
            >
              Record decline
            </button>
          </>
        )}

        {nomination.state === "REGULATOR_REVIEW_PENDING" && (
          <div className="flex flex-wrap items-center gap-1.5">
            <input
              value={authority}
              onChange={(e) => setAuthority(e.target.value)}
              placeholder="Reviewing authority"
              className="rounded-md border border-border bg-background px-2 py-1 text-xs"
            />
            <button
              type="button"
              disabled={!authority.trim() || review.isPending}
              onClick={() => {
                setError(null);
                review.mutate(
                  { nominationId: nomination.nominationId, authority: authority.trim(), approved: true, ...mutationCtx },
                  { onError: (e) => setError(errMsg(e, "Review could not be recorded.")) },
                );
              }}
              className="rounded-md border border-emerald-300 px-2 py-1 text-xs font-medium text-emerald-700 disabled:opacity-50"
            >
              Approve
            </button>
            <button
              type="button"
              disabled={!authority.trim() || review.isPending}
              onClick={() => {
                setError(null);
                review.mutate(
                  { nominationId: nomination.nominationId, authority: authority.trim(), approved: false, ...mutationCtx },
                  { onError: (e) => setError(errMsg(e, "Review could not be recorded.")) },
                );
              }}
              className="rounded-md border border-red-300 px-2 py-1 text-xs font-medium text-red-600 disabled:opacity-50"
            >
              Reject
            </button>
          </div>
        )}

        {nomination.state === "APPROVED" && (
          <button
            type="button"
            disabled={activate.isPending}
            onClick={() => {
              setError(null);
              activate.mutate(
                { nominationId: nomination.nominationId, ...mutationCtx },
                { onError: (e) => setError(errMsg(e, "Activation failed.")) },
              );
            }}
            className="rounded-md bg-primary px-2 py-1 text-xs font-medium text-primary-foreground disabled:opacity-50"
          >
            {activate.isPending ? "Activating…" : "Activate"}
          </button>
        )}

        {nomination.state === "ACTIVATED" && nomination.activatedAssignmentId !== null && (
          <span className="text-[11px] text-muted-foreground">
            Assignment #{nomination.activatedAssignmentId}
            {nomination.reviewState ? ` · review ${nomination.reviewState}` : ""}
          </span>
        )}

        {!terminal && (
          <button
            type="button"
            disabled={withdraw.isPending}
            onClick={() => {
              setError(null);
              withdraw.mutate(
                { nominationId: nomination.nominationId, ...mutationCtx },
                { onError: (e) => setError(errMsg(e, "Withdrawal failed.")) },
              );
            }}
            className="ml-auto rounded-md border border-border px-2 py-1 text-xs font-medium text-muted-foreground disabled:opacity-50"
          >
            Withdraw
          </button>
        )}
      </div>
    </li>
  );
}

/* ─── Requests for information (RFI) on an application ─── */

function InformationRequestsPanel({ profile }: { profile: FacilityProfile }) {
  const [applicationId, setApplicationId] = useState(profile.applications[0]?.applicationId ?? "");
  const rfisQuery = useInformationRequests(applicationId || undefined);
  const openRfi = useOpenInformationRequest();
  const respond = useRespondInformationRequest();
  const close = useCloseInformationRequest();
  const [message, setMessage] = useState("");
  const [dueDate, setDueDate] = useState("");
  const [respondingId, setRespondingId] = useState<string | null>(null);
  const [responseNotes, setResponseNotes] = useState("");
  const [error, setError] = useState<string | null>(null);

  const rfis = rfisQuery.data?.data ?? [];

  return (
    <Panel title="Requests for information" icon={MailQuestion}>
      {error && <p className="mb-2 text-xs text-red-600">{error}</p>}
      <select
        value={applicationId}
        onChange={(e) => setApplicationId(e.target.value)}
        className="mb-2 w-full rounded-md border border-border bg-background px-2 py-1.5 text-xs"
      >
        <option value="">Application…</option>
        {profile.applications.map((a) => (
          <option key={a.applicationId} value={a.applicationId}>
            {a.applicationType.replace(/_/g, " ")} · {a.currentWorkflowState}
          </option>
        ))}
      </select>
      {!applicationId && (
        <p className="text-xs text-muted-foreground">Select an application to see its information requests.</p>
      )}
      <ul className="mb-3 space-y-2">
        {rfis.map((r) => (
          <li key={r.rfiId} className="rounded-md border border-border/60 p-2">
            <div className="flex items-center justify-between gap-2">
              <p className="text-xs text-foreground">{r.message}</p>
              <span
                className={`rounded-full px-2 py-0.5 text-[10px] uppercase tracking-wide ${
                  r.status === "OPEN"
                    ? "bg-amber-500/10 text-amber-600"
                    : r.status === "RESPONDED"
                      ? "bg-emerald-500/10 text-emerald-600"
                      : "bg-muted text-muted-foreground"
                }`}
              >
                {r.status}
              </span>
            </div>
            <p className="mt-0.5 text-[11px] text-muted-foreground">
              Requested {fmtDate(r.requestedAt)}
              {r.dueDate ? ` · due ${fmtDate(r.dueDate)}` : ""}
              {r.responseNotes ? ` · response: ${r.responseNotes}` : ""}
            </p>
            <div className="mt-1 flex flex-wrap items-center gap-1.5">
              {r.status === "OPEN" && respondingId !== r.rfiId && (
                <button
                  type="button"
                  onClick={() => {
                    setRespondingId(r.rfiId);
                    setResponseNotes("");
                  }}
                  className="rounded-md border border-border px-2 py-0.5 text-[11px] font-medium"
                >
                  Respond
                </button>
              )}
              {r.status === "OPEN" && respondingId === r.rfiId && (
                <>
                  <input
                    value={responseNotes}
                    onChange={(e) => setResponseNotes(e.target.value)}
                    placeholder="Response notes"
                    className="flex-1 rounded-md border border-border bg-background px-2 py-1 text-xs"
                  />
                  <button
                    type="button"
                    disabled={!responseNotes.trim() || respond.isPending}
                    onClick={() => {
                      setError(null);
                      respond.mutate(
                        { rfiId: r.rfiId, responseNotes: responseNotes.trim(), applicationId },
                        {
                          onSuccess: () => setRespondingId(null),
                          onError: (e) => setError(errMsg(e, "Response could not be recorded.")),
                        },
                      );
                    }}
                    className="rounded-md bg-primary px-2 py-1 text-[11px] font-medium text-primary-foreground disabled:opacity-50"
                  >
                    Save response
                  </button>
                </>
              )}
              {r.status !== "CLOSED" && (
                <button
                  type="button"
                  disabled={close.isPending}
                  onClick={() => {
                    setError(null);
                    close.mutate(
                      { rfiId: r.rfiId, applicationId },
                      { onError: (e) => setError(errMsg(e, "RFI could not be closed.")) },
                    );
                  }}
                  className="rounded-md border border-border px-2 py-0.5 text-[11px] font-medium text-muted-foreground disabled:opacity-50"
                >
                  Close
                </button>
              )}
            </div>
          </li>
        ))}
        {applicationId && !rfisQuery.isLoading && rfis.length === 0 && (
          <li className="text-xs text-muted-foreground">No information requests on this application.</li>
        )}
      </ul>
      {applicationId && (
        <div className="border-t border-border/60 pt-3">
          <p className="mb-2 text-[11px] uppercase tracking-wide text-muted-foreground">Open request</p>
          <div className="flex flex-wrap gap-2">
            <input
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              placeholder="What further information is required?"
              className="flex-1 rounded-md border border-border bg-background px-2 py-1.5 text-xs"
            />
            <input
              type="date"
              value={dueDate}
              onChange={(e) => setDueDate(e.target.value)}
              className="rounded-md border border-border bg-background px-2 py-1.5 text-xs"
            />
            <button
              type="button"
              disabled={!message.trim() || openRfi.isPending}
              onClick={() => {
                setError(null);
                openRfi.mutate(
                  { applicationId, message: message.trim(), dueDate: dueDate || undefined },
                  {
                    onSuccess: () => {
                      setMessage("");
                      setDueDate("");
                    },
                    onError: (e) => setError(errMsg(e, "Request could not be opened.")),
                  },
                );
              }}
              className="rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground disabled:opacity-50"
            >
              {openRfi.isPending ? "Opening…" : "Open RFI"}
            </button>
          </div>
        </div>
      )}
    </Panel>
  );
}

/* ─── Council reviews (local-authority / council endorsements on an application) ─── */

const COUNCIL_REVIEW_STATUSES = ["PENDING", "ENDORSED", "REJECTED", "NOT_REQUIRED"];

function CouncilReviewPanel({ profile }: { profile: FacilityProfile }) {
  const [applicationId, setApplicationId] = useState(profile.applications[0]?.applicationId ?? "");
  const reviewsQuery = useCouncilReviews(applicationId || undefined);
  const record = useRecordCouncilReview();
  const [reviewingAuthority, setReviewingAuthority] = useState("");
  const [referenceNumber, setReferenceNumber] = useState("");
  const [status, setStatus] = useState("PENDING");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);

  const reviews = reviewsQuery.data?.data ?? [];

  return (
    <Panel title="Council reviews" icon={Landmark}>
      {error && <p className="mb-2 text-xs text-red-600">{error}</p>}
      <select
        value={applicationId}
        onChange={(e) => setApplicationId(e.target.value)}
        className="mb-2 w-full rounded-md border border-border bg-background px-2 py-1.5 text-xs"
      >
        <option value="">Application…</option>
        {profile.applications.map((a) => (
          <option key={a.applicationId} value={a.applicationId}>
            {a.applicationType.replace(/_/g, " ")} · {a.currentWorkflowState}
          </option>
        ))}
      </select>
      {!applicationId && (
        <p className="text-xs text-muted-foreground">Select an application to see its council reviews.</p>
      )}
      <ul className="mb-3 space-y-1">
        {reviews.map((r) => (
          <li key={r.reviewId} className="flex items-center justify-between text-xs">
            <span className="text-foreground">
              {r.reviewingAuthority}
              {r.referenceNumber ? <span className="ml-1 text-muted-foreground">({r.referenceNumber})</span> : null}
            </span>
            <span
              className={
                r.status === "ENDORSED"
                  ? "text-emerald-600"
                  : r.status === "REJECTED"
                    ? "text-red-600"
                    : "text-muted-foreground"
              }
            >
              {r.status.replace(/_/g, " ")} · {fmtDate(r.recordedAt)}
            </span>
          </li>
        ))}
        {applicationId && !reviewsQuery.isLoading && reviews.length === 0 && (
          <li className="text-xs text-muted-foreground">No council reviews recorded for this application.</li>
        )}
      </ul>
      {applicationId && (
        <div className="border-t border-border/60 pt-3">
          <div className="grid gap-2 sm:grid-cols-2">
            <input
              value={reviewingAuthority}
              onChange={(e) => setReviewingAuthority(e.target.value)}
              placeholder="Reviewing authority (e.g. City of Harare)"
              className="rounded-md border border-border bg-background px-2 py-1.5 text-xs"
            />
            <input
              value={referenceNumber}
              onChange={(e) => setReferenceNumber(e.target.value)}
              placeholder="Reference number"
              className="rounded-md border border-border bg-background px-2 py-1.5 text-xs"
            />
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              className="rounded-md border border-border bg-background px-2 py-1.5 text-xs"
            >
              {COUNCIL_REVIEW_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s.replace(/_/g, " ")}
                </option>
              ))}
            </select>
            <input
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Notes"
              className="rounded-md border border-border bg-background px-2 py-1.5 text-xs"
            />
          </div>
          <button
            type="button"
            disabled={!reviewingAuthority.trim() || record.isPending}
            onClick={() => {
              setError(null);
              record.mutate(
                {
                  applicationId,
                  reviewingAuthority: reviewingAuthority.trim(),
                  referenceNumber: referenceNumber.trim() || undefined,
                  status,
                  notes: notes.trim() || undefined,
                },
                {
                  onSuccess: () => {
                    setReviewingAuthority("");
                    setReferenceNumber("");
                    setNotes("");
                  },
                  onError: (e) => setError(errMsg(e, "Council review could not be recorded.")),
                },
              );
            }}
            className="mt-2 rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground disabled:opacity-50"
          >
            {record.isPending ? "Recording…" : "Record review"}
          </button>
        </div>
      )}
    </Panel>
  );
}

/* ─── Premises & occupancy (physical premises register + tenure history) ─── */

function PremisesPanel({ facilityId }: { facilityId: number }) {
  const historyQuery = useOccupancyHistory(facilityId);
  const premisesQuery = usePremises();
  const createPremises = useCreatePremises();
  const assign = useAssignOccupancy();
  const [label, setLabel] = useState("");
  const [city, setCity] = useState("");
  const [province, setProvince] = useState("");
  const [selectedPremisesId, setSelectedPremisesId] = useState("");
  const [role, setRole] = useState("PRIMARY");
  const [error, setError] = useState<string | null>(null);

  const history = historyQuery.data?.data ?? [];
  const premises = premisesQuery.data?.data ?? [];
  const labelFor = (premisesId: number) =>
    premises.find((p) => p.premisesId === premisesId)?.label ?? `Premises #${premisesId}`;

  return (
    <Panel title="Premises & occupancy" icon={Building2}>
      {error && <p className="mb-2 text-xs text-red-600">{error}</p>}
      <p className="mb-1 text-[11px] uppercase tracking-wide text-muted-foreground">Occupancy history</p>
      <ul className="mb-3 space-y-1">
        {history.map((h, idx) => (
          <li key={h.occupancyId ?? `${h.premisesId}-${idx}`} className="flex items-center justify-between text-xs">
            <span className="text-foreground">
              {h.premisesLabel ?? labelFor(h.premisesId)}
              <span className="ml-1 text-muted-foreground">{h.occupancyRole.replace(/_/g, " ")}</span>
            </span>
            <span className="text-muted-foreground">
              {fmtDate(h.effectiveFrom)} → {h.effectiveTo ? fmtDate(h.effectiveTo) : "current"}
            </span>
          </li>
        ))}
        {!historyQuery.isLoading && history.length === 0 && (
          <li className="text-xs text-muted-foreground">No premises occupancy on record for this facility.</li>
        )}
      </ul>

      <div className="border-t border-border/60 pt-3">
        <p className="mb-2 text-[11px] uppercase tracking-wide text-muted-foreground">Register premises</p>
        <div className="grid gap-2 sm:grid-cols-3">
          <input
            value={label}
            onChange={(e) => setLabel(e.target.value)}
            placeholder="Premises label"
            className="rounded-md border border-border bg-background px-2 py-1.5 text-xs"
          />
          <input
            value={city}
            onChange={(e) => setCity(e.target.value)}
            placeholder="City"
            className="rounded-md border border-border bg-background px-2 py-1.5 text-xs"
          />
          <input
            value={province}
            onChange={(e) => setProvince(e.target.value)}
            placeholder="Province"
            className="rounded-md border border-border bg-background px-2 py-1.5 text-xs"
          />
        </div>
        <button
          type="button"
          disabled={!label.trim() || createPremises.isPending}
          onClick={() => {
            setError(null);
            createPremises.mutate(
              { label: label.trim(), city: city.trim() || undefined, province: province.trim() || undefined },
              {
                onSuccess: (response) => {
                  setLabel("");
                  setCity("");
                  setProvince("");
                  setSelectedPremisesId(String(response.data.premisesId));
                },
                onError: (e) => setError(errMsg(e, "Premises could not be registered.")),
              },
            );
          }}
          className="mt-2 rounded-md border border-border px-3 py-1.5 text-xs font-medium disabled:opacity-50"
        >
          {createPremises.isPending ? "Registering…" : "Register premises"}
        </button>
      </div>

      <div className="mt-3 border-t border-border/60 pt-3">
        <p className="mb-2 text-[11px] uppercase tracking-wide text-muted-foreground">Assign this facility to premises</p>
        <div className="flex flex-wrap gap-2">
          <select
            value={selectedPremisesId}
            onChange={(e) => setSelectedPremisesId(e.target.value)}
            className="flex-1 rounded-md border border-border bg-background px-2 py-1.5 text-xs"
          >
            <option value="">Premises…</option>
            {premises.map((p) => (
              <option key={p.premisesId} value={String(p.premisesId)}>
                {p.label}
                {p.city ? ` · ${p.city}` : ""}
              </option>
            ))}
          </select>
          <select
            value={role}
            onChange={(e) => setRole(e.target.value)}
            className="rounded-md border border-border bg-background px-2 py-1.5 text-xs"
          >
            <option value="PRIMARY">PRIMARY</option>
            <option value="SHARED_OCCUPANT">SHARED OCCUPANT</option>
          </select>
          <button
            type="button"
            disabled={!selectedPremisesId || assign.isPending}
            onClick={() => {
              setError(null);
              assign.mutate(
                { premisesId: Number(selectedPremisesId), facilityId, role },
                { onError: (e) => setError(errMsg(e, "Occupancy could not be assigned.")) },
              );
            }}
            className="rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground disabled:opacity-50"
          >
            {assign.isPending ? "Assigning…" : "Assign occupancy"}
          </button>
        </div>
      </div>
    </Panel>
  );
}
