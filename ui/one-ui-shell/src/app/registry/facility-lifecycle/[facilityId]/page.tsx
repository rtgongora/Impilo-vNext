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

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Award, FileText, Gavel, Loader2, ShieldAlert, Stethoscope, UserCheck } from "lucide-react";
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
            <CommitteePanel facilityId={facilityId} profile={profile} />
            <CertificatesPanel profile={profile} />
            <PractitionerPanel profile={profile} />
            <EnforcementPanel facilityId={facilityId} profile={profile} />
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
