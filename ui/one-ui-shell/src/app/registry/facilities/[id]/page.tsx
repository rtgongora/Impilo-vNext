"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import {
  AlertTriangle,
  ArrowLeft,
  BadgeCheck,
  ClipboardList,
  FileClock,
  FileText,
  LayoutGrid,
  Loader2,
  ShieldAlert,
  Stamp,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useChecklistTemplates,
  useCreateFacilityApplication,
  useFacilityProfile,
  useFacilityStatusSummary,
  useMarkApplicationReadyForInspection,
  useOpenEnforcementCase,
  useRecordCommitteeDecision,
  useRecordFacilityInspection,
  useScheduleFacilityInspection,
  useSubmitFacilityApplication,
  useUpdateComplianceAction,
  useUploadFacilityDocument,
} from "@/hooks/queries/useFacilityRegulatory";

const tabs = [
  { id: "master", label: "Master profile" },
  { id: "applications", label: "Applications" },
  { id: "inspections", label: "Inspections" },
  { id: "findings", label: "Findings / shortfalls" },
  { id: "certificates", label: "Certificates" },
  { id: "audit", label: "Audit / history" },
  { id: "units", label: "Units / departments" },
] as const;

type TabId = (typeof tabs)[number]["id"];

function formatLabel(value: string | null | undefined) {
  if (!value) return "Not stated";
  return value.replaceAll("_", " ");
}

function statusTone(status: string) {
  if (status.includes("ACTIVE")) return "bg-emerald-50 text-emerald-700 border-emerald-200";
  if (status.includes("PENDING")) return "bg-amber-50 text-amber-700 border-amber-200";
  if (status.includes("RECTIFICATION") || status.includes("RESTRICTED")) return "bg-rose-50 text-rose-700 border-rose-200";
  if (status.includes("SUSPENDED") || status.includes("CLOSED")) return "bg-red-50 text-red-700 border-red-200";
  return "bg-gray-100 text-gray-700 border-gray-200";
}

export default function FacilityDetailPage() {
  const params = useParams();
  const id = params.id as string;
  const [activeTab, setActiveTab] = useState<TabId>("master");
  const [selectedInspectionId, setSelectedInspectionId] = useState<string>("");
  const [scheduleForm, setScheduleForm] = useState({
    applicationId: "",
    inspectionType: "INITIAL",
    scheduledDate: "",
  });
  const [documentForm, setDocumentForm] = useState({
    applicationId: "",
    documentType: "APPLICATION_FORM",
    fileReference: "",
    notes: "",
  });
  const [committeeForm, setCommitteeForm] = useState({
    applicationId: "",
    inspectionId: "",
    committeeType: "HPA_MAIN_COMMITTEE",
    decision: "APPROVED",
    notes: "",
    authorityContext: "HPA_COMMITTEE",
  });
  const [enforcementForm, setEnforcementForm] = useState({
    triggerType: "INVESTIGATIVE_INSPECTION",
    status: "RECOMMENDED_RESTRICTION",
    recommendation: "Restrict operations until shortfalls are verified",
    authorityRoute: "HPA_EXECUTIVE",
    decisionDetails: "",
  });

  const profileQuery = useFacilityProfile(id);
  const profile = profileQuery.data?.data;
  const master = profile?.master;
  const statusSummaryQuery = useFacilityStatusSummary(master?.facilityId);
  const statusSummary = statusSummaryQuery.data?.data;

  const createApplication = useCreateFacilityApplication();
  const submitApplication = useSubmitFacilityApplication();
  const readyForInspection = useMarkApplicationReadyForInspection();
  const uploadDocument = useUploadFacilityDocument();
  const scheduleInspection = useScheduleFacilityInspection();
  const recordInspection = useRecordFacilityInspection();
  const updateComplianceAction = useUpdateComplianceAction();
  const recordCommitteeDecision = useRecordCommitteeDecision();
  const openEnforcementCase = useOpenEnforcementCase();

  const selectedInspection =
    profile?.inspections.find((inspection) => inspection.inspectionId === selectedInspectionId) ??
    profile?.inspections.find((inspection) => inspection.status === "SCHEDULED") ??
    null;

  const checklistQuery = useChecklistTemplates(selectedInspection?.inspectionType, master?.facilityType);
  const checklistItems = (checklistQuery.data?.data ?? [])?.[0]?.items as Array<Record<string, unknown>> | undefined;

  const [findingStates, setFindingStates] = useState<Record<string, { status: string; comments: string }>>({});

  const openApplications = useMemo(
    () =>
      profile?.applications.filter((application) =>
        !["DECIDED_APPROVED", "DECIDED_REJECTED", "CLOSED_OUT"].includes(application.currentWorkflowState),
      ) ?? [],
    [profile?.applications],
  );

  async function launchMaterialChange(applicationType: string) {
    if (!master) return;
    createApplication.mutate({
      facilityId: master.facilityId,
      applicationType,
      applicantName: "Current operator",
      facilityName: master.name,
      facilityCode: master.facilityCode,
      ownershipType: master.ownershipType ?? undefined,
      facilityClass: master.facilityClass ?? undefined,
      facilityCategory: master.facilityCategory ?? undefined,
      facilityType: master.facilityType ?? undefined,
      legalStatus: master.legalStatus ?? undefined,
      registrationPathway: master.registrationPathway ?? undefined,
      province: master.province ?? undefined,
      district: master.district ?? undefined,
      city: master.city ?? undefined,
      ward: master.ward ?? undefined,
      addressLine1: master.addressLine1 ?? undefined,
      addressLine2: master.addressLine2 ?? undefined,
      workflowNotes: `Opened from facility workspace for ${applicationType.replaceAll("_", " ").toLowerCase()}.`,
    });
  }

  function handleInspectionRecordSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!selectedInspection || !master || !checklistItems) return;
    const findings = checklistItems.slice(0, 4).map((item) => {
      const code = String(item.code ?? "");
      const state = findingStates[code] ?? { status: "PASS", comments: "" };
      return {
        checklistItemCode: code,
        checklistSection: String(item.section ?? ""),
        requirementText: String(item.requirement ?? ""),
        criticalFlag: Boolean(item.critical),
        status: state.status,
        comments: state.comments || undefined,
        severity: String(item.severity ?? "MODERATE"),
      };
    });
    recordInspection.mutate({
      inspectionId: selectedInspection.inspectionId,
      facilityId: master.facilityId,
      body: {
        actualDate: new Date().toISOString().slice(0, 10),
        findings,
      },
    });
  }

  return (
    <AppLayout>
      <PageShell
        title="Facility Operations Workspace"
        subtitle="Registration, inspection, compliance, committee, certificate, and audit operations for one facility"
      >
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <Link href="/registry/facilities" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
            <ArrowLeft className="h-4 w-4" />
            Back to facility operations
          </Link>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => launchMaterialChange("RENEWAL")}
              className="rounded-xl border border-gray-300 px-3 py-2 text-xs font-medium text-gray-700 hover:border-emerald-300 hover:text-emerald-700"
            >
              Renewal workflow
            </button>
            <button
              type="button"
              onClick={() => launchMaterialChange("ADD_UNIT")}
              className="rounded-xl border border-gray-300 px-3 py-2 text-xs font-medium text-gray-700 hover:border-emerald-300 hover:text-emerald-700"
            >
              Add unit
            </button>
            <button
              type="button"
              onClick={() => launchMaterialChange("CHANGE_OF_PRACTITIONER_IN_CHARGE")}
              className="rounded-xl border border-gray-300 px-3 py-2 text-xs font-medium text-gray-700 hover:border-emerald-300 hover:text-emerald-700"
            >
              Change PIC
            </button>
            <button
              type="button"
              onClick={() => launchMaterialChange("CHANGE_OF_PREMISES")}
              className="rounded-xl border border-gray-300 px-3 py-2 text-xs font-medium text-gray-700 hover:border-emerald-300 hover:text-emerald-700"
            >
              Change premises
            </button>
          </div>
        </div>

        {profileQuery.isLoading ? (
          <div className="flex items-center justify-center py-16 text-gray-500">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" />
            Loading facility operations workspace...
          </div>
        ) : profileQuery.isError || !profile || !master ? (
          <div className="rounded-2xl border border-red-200 bg-red-50 p-6 text-center text-sm text-red-700">
            Facility lifecycle data could not be loaded.
          </div>
        ) : (
          <div className="space-y-6">
            <div className="rounded-3xl border border-gray-200 bg-white p-6">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="text-2xl font-semibold text-gray-900">{master.name}</h2>
                    <span className={`rounded-full border px-3 py-1 text-xs font-semibold ${statusTone(master.regulatoryStatus)}`}>
                      {formatLabel(master.regulatoryStatus)}
                    </span>
                    {statusSummary ? (
                      <span className={`rounded-full border px-3 py-1 text-xs font-semibold ${statusTone(statusSummary.status)}`}>
                        {formatLabel(statusSummary.status)}
                      </span>
                    ) : null}
                  </div>
                  <p className="mt-2 text-sm text-gray-500">
                    {master.facilityCode} • {master.facilityType} • {master.institutionFileNumber ?? "Institution file pending"}
                  </p>
                </div>
                <div className="grid gap-3 text-sm md:grid-cols-3">
                  <div className="rounded-2xl bg-gray-50 p-4">
                    <p className="text-xs uppercase tracking-[0.18em] text-gray-500">Applications</p>
                    <p className="mt-2 text-2xl font-semibold text-gray-900">{profile.applications.length}</p>
                  </div>
                  <div className="rounded-2xl bg-gray-50 p-4">
                    <p className="text-xs uppercase tracking-[0.18em] text-gray-500">Inspections</p>
                    <p className="mt-2 text-2xl font-semibold text-gray-900">{profile.inspections.length}</p>
                  </div>
                  <div className="rounded-2xl bg-gray-50 p-4">
                    <p className="text-xs uppercase tracking-[0.18em] text-gray-500">Open shortfalls</p>
                    <p className="mt-2 text-2xl font-semibold text-gray-900">
                      {profile.complianceActions.filter((action) => !["VERIFIED", "COMPLETED"].includes(action.status)).length}
                    </p>
                  </div>
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
                    activeTab === tab.id ? "bg-emerald-600 text-white" : "bg-white text-gray-600 hover:bg-emerald-50 hover:text-emerald-700"
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            {activeTab === "master" ? (
              <div className="grid gap-4 lg:grid-cols-[1.1fr_0.9fr]">
                <div className="rounded-2xl border border-gray-200 bg-white p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Facility master</h3>
                  <dl className="mt-4 grid gap-4 md:grid-cols-2">
                    <div>
                      <dt className="text-xs text-gray-500">Ownership</dt>
                      <dd className="mt-1 font-medium text-gray-900">{formatLabel(master.ownershipType)}</dd>
                    </div>
                    <div>
                      <dt className="text-xs text-gray-500">Pathway</dt>
                      <dd className="mt-1 font-medium text-gray-900">{formatLabel(master.registrationPathway)}</dd>
                    </div>
                    <div>
                      <dt className="text-xs text-gray-500">Facility class</dt>
                      <dd className="mt-1 font-medium text-gray-900">{formatLabel(master.facilityClass)}</dd>
                    </div>
                    <div>
                      <dt className="text-xs text-gray-500">Facility category</dt>
                      <dd className="mt-1 font-medium text-gray-900">{formatLabel(master.facilityCategory)}</dd>
                    </div>
                    <div>
                      <dt className="text-xs text-gray-500">Legal status</dt>
                      <dd className="mt-1 font-medium text-gray-900">{formatLabel(master.legalStatus)}</dd>
                    </div>
                    <div>
                      <dt className="text-xs text-gray-500">Operational status</dt>
                      <dd className="mt-1 font-medium text-gray-900">{formatLabel(master.operationalStatus)}</dd>
                    </div>
                  </dl>
                </div>
                <div className="rounded-2xl border border-gray-200 bg-white p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Premises</h3>
                  <p className="mt-4 text-sm text-gray-700">
                    {[master.addressLine1, master.addressLine2, master.city, master.ward, master.district, master.province]
                      .filter(Boolean)
                      .join(", ") || "Address not yet populated"}
                  </p>
                  <div className="mt-4 grid gap-3 md:grid-cols-2">
                    <div className="rounded-2xl bg-gray-50 p-4">
                      <p className="text-xs uppercase tracking-[0.18em] text-gray-500">Facility status</p>
                      <p className="mt-2 font-semibold text-gray-900">{formatLabel(master.status)}</p>
                    </div>
                    <div className="rounded-2xl bg-gray-50 p-4">
                      <p className="text-xs uppercase tracking-[0.18em] text-gray-500">Created</p>
                      <p className="mt-2 font-semibold text-gray-900">{new Date(master.createdAt).toLocaleDateString()}</p>
                    </div>
                  </div>
                </div>
              </div>
            ) : null}

            {activeTab === "applications" ? (
              <div className="grid gap-4 lg:grid-cols-[1.2fr_0.8fr]">
                <div className="rounded-2xl border border-gray-200 bg-white p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Application queue</h3>
                  <div className="mt-4 space-y-3">
                    {profile.applications.map((application) => (
                      <div key={application.applicationId} className="rounded-2xl border border-gray-200 p-4">
                        <div className="flex flex-wrap items-start justify-between gap-3">
                          <div>
                            <p className="font-medium text-gray-900">{formatLabel(application.applicationType)}</p>
                            <p className="mt-1 text-xs text-gray-500">
                              {formatLabel(application.currentWorkflowState)} • Fee {formatLabel(application.feeState)}
                            </p>
                          </div>
                          <div className="flex gap-2">
                            {application.currentWorkflowState === "DRAFT" ? (
                              <button
                                type="button"
                                onClick={() =>
                                  submitApplication.mutate({
                                    applicationId: application.applicationId,
                                    facilityId: master.facilityId,
                                  })
                                }
                                className="rounded-lg border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-700 hover:border-emerald-300 hover:text-emerald-700"
                              >
                                Submit
                              </button>
                            ) : null}
                            {["SUBMITTED", "UNDER_ADMIN_REVIEW", "AWAITING_DOCUMENTS", "AWAITING_FEE"].includes(
                              application.currentWorkflowState,
                            ) ? (
                              <button
                                type="button"
                                onClick={() =>
                                  readyForInspection.mutate({
                                    applicationId: application.applicationId,
                                    facilityId: master.facilityId,
                                  })
                                }
                                className="rounded-lg border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-700 hover:border-emerald-300 hover:text-emerald-700"
                              >
                                Ready for inspection
                              </button>
                            ) : null}
                          </div>
                        </div>
                        {application.workflowNotes ? (
                          <p className="mt-3 text-sm text-gray-600">{application.workflowNotes}</p>
                        ) : null}
                      </div>
                    ))}
                  </div>
                </div>

                <div className="space-y-4">
                  <div className="rounded-2xl border border-gray-200 bg-white p-5">
                    <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Upload evidence / dossier</h3>
                    <form
                      className="mt-4 space-y-3"
                      onSubmit={(event) => {
                        event.preventDefault();
                        uploadDocument.mutate({
                          facilityId: master.facilityId,
                          applicationId: documentForm.applicationId || undefined,
                          documentType: documentForm.documentType,
                          fileReference: documentForm.fileReference,
                          notes: documentForm.notes || undefined,
                        });
                      }}
                    >
                      <select
                        value={documentForm.applicationId}
                        onChange={(event) => setDocumentForm((current) => ({ ...current, applicationId: event.target.value }))}
                        className="w-full rounded-xl border border-gray-300 px-3 py-2.5 text-sm"
                      >
                        <option value="">Attach to no specific application</option>
                        {openApplications.map((application) => (
                          <option key={application.applicationId} value={application.applicationId}>
                            {formatLabel(application.applicationType)} ({formatLabel(application.currentWorkflowState)})
                          </option>
                        ))}
                      </select>
                      <input
                        value={documentForm.documentType}
                        onChange={(event) => setDocumentForm((current) => ({ ...current, documentType: event.target.value }))}
                        className="w-full rounded-xl border border-gray-300 px-3 py-2.5 text-sm"
                        placeholder="Document type"
                      />
                      <input
                        value={documentForm.fileReference}
                        onChange={(event) => setDocumentForm((current) => ({ ...current, fileReference: event.target.value }))}
                        className="w-full rounded-xl border border-gray-300 px-3 py-2.5 text-sm"
                        placeholder="Document store reference / URI"
                      />
                      <textarea
                        rows={3}
                        value={documentForm.notes}
                        onChange={(event) => setDocumentForm((current) => ({ ...current, notes: event.target.value }))}
                        className="w-full rounded-xl border border-gray-300 px-3 py-2.5 text-sm"
                        placeholder="Verification or dossier notes"
                      />
                      <button className="rounded-xl bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700" type="submit">
                        Add document
                      </button>
                    </form>
                  </div>

                  <div className="rounded-2xl border border-gray-200 bg-white p-5">
                    <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Committee decision capture</h3>
                    <form
                      className="mt-4 space-y-3"
                      onSubmit={(event) => {
                        event.preventDefault();
                        if (!committeeForm.applicationId) return;
                        recordCommitteeDecision.mutate({
                          facilityId: master.facilityId,
                          applicationId: committeeForm.applicationId,
                          inspectionId: committeeForm.inspectionId || undefined,
                          committeeType: committeeForm.committeeType,
                          decision: committeeForm.decision,
                          notes: committeeForm.notes || undefined,
                          authorityContext: committeeForm.authorityContext,
                          issueCertificate: committeeForm.decision === "APPROVED",
                        });
                      }}
                    >
                      <select
                        value={committeeForm.applicationId}
                        onChange={(event) => setCommitteeForm((current) => ({ ...current, applicationId: event.target.value }))}
                        className="w-full rounded-xl border border-gray-300 px-3 py-2.5 text-sm"
                      >
                        <option value="">Select application</option>
                        {openApplications.map((application) => (
                          <option key={application.applicationId} value={application.applicationId}>
                            {formatLabel(application.applicationType)}
                          </option>
                        ))}
                      </select>
                      <select
                        value={committeeForm.inspectionId}
                        onChange={(event) => setCommitteeForm((current) => ({ ...current, inspectionId: event.target.value }))}
                        className="w-full rounded-xl border border-gray-300 px-3 py-2.5 text-sm"
                      >
                        <option value="">No linked inspection</option>
                        {profile.inspections.map((inspection) => (
                          <option key={inspection.inspectionId} value={inspection.inspectionId}>
                            {formatLabel(inspection.inspectionType)} • {formatLabel(inspection.outcome)}
                          </option>
                        ))}
                      </select>
                      <select
                        value={committeeForm.decision}
                        onChange={(event) => setCommitteeForm((current) => ({ ...current, decision: event.target.value }))}
                        className="w-full rounded-xl border border-gray-300 px-3 py-2.5 text-sm"
                      >
                        <option value="APPROVED">Approve</option>
                        <option value="DEFERRED">Defer</option>
                        <option value="REJECTED">Reject</option>
                      </select>
                      <textarea
                        rows={3}
                        value={committeeForm.notes}
                        onChange={(event) => setCommitteeForm((current) => ({ ...current, notes: event.target.value }))}
                        className="w-full rounded-xl border border-gray-300 px-3 py-2.5 text-sm"
                        placeholder="Committee resolution or rationale"
                      />
                      <button className="rounded-xl bg-sky-600 px-4 py-2 text-sm font-medium text-white hover:bg-sky-700" type="submit">
                        Record committee decision
                      </button>
                    </form>
                  </div>
                </div>
              </div>
            ) : null}

            {activeTab === "inspections" ? (
              <div className="grid gap-4 lg:grid-cols-[0.9fr_1.1fr]">
                <div className="space-y-4">
                  <div className="rounded-2xl border border-gray-200 bg-white p-5">
                    <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Schedule inspection</h3>
                    <form
                      className="mt-4 space-y-3"
                      onSubmit={(event) => {
                        event.preventDefault();
                        scheduleInspection.mutate({
                          facilityId: master.facilityId,
                          applicationId: scheduleForm.applicationId || undefined,
                          inspectionType: scheduleForm.inspectionType,
                          scheduledDate: scheduleForm.scheduledDate || undefined,
                        });
                      }}
                    >
                      <select
                        value={scheduleForm.applicationId}
                        onChange={(event) => setScheduleForm((current) => ({ ...current, applicationId: event.target.value }))}
                        className="w-full rounded-xl border border-gray-300 px-3 py-2.5 text-sm"
                      >
                        <option value="">No linked application</option>
                        {openApplications.map((application) => (
                          <option key={application.applicationId} value={application.applicationId}>
                            {formatLabel(application.applicationType)}
                          </option>
                        ))}
                      </select>
                      <select
                        value={scheduleForm.inspectionType}
                        onChange={(event) => setScheduleForm((current) => ({ ...current, inspectionType: event.target.value }))}
                        className="w-full rounded-xl border border-gray-300 px-3 py-2.5 text-sm"
                      >
                        <option value="INITIAL">Initial</option>
                        <option value="ROUTINE">Routine</option>
                        <option value="JOINT">Joint</option>
                        <option value="MULTIDISCIPLINARY">Multidisciplinary</option>
                        <option value="INVESTIGATIVE">Investigative</option>
                        <option value="FOLLOW_UP">Follow-up</option>
                        <option value="VERIFICATION">Verification</option>
                      </select>
                      <input
                        type="date"
                        value={scheduleForm.scheduledDate}
                        onChange={(event) => setScheduleForm((current) => ({ ...current, scheduledDate: event.target.value }))}
                        className="w-full rounded-xl border border-gray-300 px-3 py-2.5 text-sm"
                      />
                      <button className="rounded-xl bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700" type="submit">
                        Schedule inspection
                      </button>
                    </form>
                  </div>

                  <div className="rounded-2xl border border-gray-200 bg-white p-5">
                    <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Inspection queue</h3>
                    <div className="mt-4 space-y-3">
                      {profile.inspections.map((inspection) => (
                        <button
                          key={inspection.inspectionId}
                          type="button"
                          onClick={() => setSelectedInspectionId(inspection.inspectionId)}
                          className={`w-full rounded-2xl border p-4 text-left ${
                            selectedInspection?.inspectionId === inspection.inspectionId ? "border-emerald-300 bg-emerald-50" : "border-gray-200"
                          }`}
                        >
                          <div className="flex items-center justify-between gap-3">
                            <div>
                              <p className="font-medium text-gray-900">{formatLabel(inspection.inspectionType)}</p>
                              <p className="mt-1 text-xs text-gray-500">
                                {formatLabel(inspection.status)} • {formatLabel(inspection.outcome)}
                              </p>
                            </div>
                            <ClipboardList className="h-4 w-4 text-gray-400" />
                          </div>
                        </button>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="rounded-2xl border border-gray-200 bg-white p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Inspection workspace</h3>
                  {selectedInspection ? (
                    <form className="mt-4 space-y-4" onSubmit={handleInspectionRecordSubmit}>
                      <div className="rounded-2xl bg-gray-50 p-4">
                        <p className="text-sm font-medium text-gray-900">
                          {formatLabel(selectedInspection.inspectionType)} inspection
                        </p>
                        <p className="mt-1 text-xs text-gray-500">
                          Template: {selectedInspection.templateName ?? "No template linked"}
                        </p>
                      </div>
                      {checklistQuery.isLoading ? (
                        <div className="flex items-center text-sm text-gray-500">
                          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                          Loading checklist...
                        </div>
                      ) : checklistItems && checklistItems.length > 0 ? (
                        checklistItems.slice(0, 4).map((item) => {
                          const code = String(item.code ?? "");
                          const localState = findingStates[code] ?? { status: "PASS", comments: "" };
                          return (
                            <div key={code} className="rounded-2xl border border-gray-200 p-4">
                              <div className="flex flex-wrap items-center gap-2">
                                <p className="text-sm font-medium text-gray-900">{String(item.requirement ?? "Requirement")}</p>
                                {item.critical ? (
                                  <span className="rounded-full bg-red-50 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-red-700">
                                    Critical
                                  </span>
                                ) : null}
                              </div>
                              <p className="mt-1 text-xs text-gray-500">{String(item.section ?? "General")} • {code}</p>
                              <div className="mt-3 grid gap-3 md:grid-cols-[180px_1fr]">
                                <select
                                  value={localState.status}
                                  onChange={(event) =>
                                    setFindingStates((current) => ({
                                      ...current,
                                      [code]: { ...localState, status: event.target.value },
                                    }))
                                  }
                                  className="rounded-xl border border-gray-300 px-3 py-2.5 text-sm"
                                >
                                  <option value="PASS">Pass</option>
                                  <option value="FAIL">Fail</option>
                                  <option value="OBSERVATION">Observation</option>
                                  <option value="NOT_APPLICABLE">Not applicable</option>
                                </select>
                                <input
                                  value={localState.comments}
                                  onChange={(event) =>
                                    setFindingStates((current) => ({
                                      ...current,
                                      [code]: { ...localState, comments: event.target.value },
                                    }))
                                  }
                                  className="rounded-xl border border-gray-300 px-3 py-2.5 text-sm"
                                  placeholder="Comments, observations, or shortfall note"
                                />
                              </div>
                            </div>
                          );
                        })
                      ) : (
                        <div className="rounded-2xl border border-dashed border-gray-300 p-6 text-sm text-gray-500">
                          No checklist template is available for this inspection yet.
                        </div>
                      )}
                      <button className="rounded-xl bg-sky-600 px-4 py-2 text-sm font-medium text-white hover:bg-sky-700" type="submit">
                        Record inspection outcome
                      </button>
                    </form>
                  ) : (
                    <div className="rounded-2xl border border-dashed border-gray-300 p-8 text-center text-sm text-gray-500">
                      Select an inspection to capture checklist findings.
                    </div>
                  )}
                </div>
              </div>
            ) : null}

            {activeTab === "findings" ? (
              <div className="grid gap-4 lg:grid-cols-[0.95fr_1.05fr]">
                <div className="rounded-2xl border border-gray-200 bg-white p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Inspection findings</h3>
                  <div className="mt-4 space-y-3">
                    {profile.findings.map((finding) => (
                      <div key={finding.findingId} className="rounded-2xl border border-gray-200 p-4">
                        <div className="flex flex-wrap items-center gap-2">
                          <p className="font-medium text-gray-900">{finding.requirementText}</p>
                          <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em] ${
                            finding.status === "FAIL" ? "bg-rose-50 text-rose-700" : "bg-emerald-50 text-emerald-700"
                          }`}>
                            {formatLabel(finding.status)}
                          </span>
                        </div>
                        <p className="mt-1 text-xs text-gray-500">
                          {finding.checklistSection} • {finding.checklistItemCode} • Rectification {formatLabel(finding.rectificationStatus)}
                        </p>
                        {finding.comments ? <p className="mt-2 text-sm text-gray-600">{finding.comments}</p> : null}
                      </div>
                    ))}
                  </div>
                </div>

                <div className="rounded-2xl border border-gray-200 bg-white p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Shortfall tracker</h3>
                  <div className="mt-4 space-y-3">
                    {profile.complianceActions.map((action) => (
                      <div key={action.actionId} className="rounded-2xl border border-gray-200 p-4">
                        <div className="flex flex-wrap items-center justify-between gap-3">
                          <div>
                            <p className="font-medium text-gray-900">{formatLabel(action.actionType)}</p>
                            <p className="mt-1 text-xs text-gray-500">
                              Due {action.dueDate} • Owner {action.ownerName ?? "Unassigned"} • {formatLabel(action.status)}
                            </p>
                          </div>
                          <div className="flex gap-2">
                            {action.status === "OPEN" || action.status === "IN_PROGRESS" ? (
                              <button
                                type="button"
                                onClick={() =>
                                  updateComplianceAction.mutate({
                                    actionId: action.actionId,
                                    facilityId: master.facilityId,
                                    status: "COMPLETED",
                                    completionNotes: "Correction uploaded from facility workspace.",
                                  })
                                }
                                className="rounded-lg border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-700 hover:border-emerald-300 hover:text-emerald-700"
                              >
                                Mark completed
                              </button>
                            ) : null}
                            {action.status === "COMPLETED" ? (
                              <button
                                type="button"
                                onClick={() =>
                                  updateComplianceAction.mutate({
                                    actionId: action.actionId,
                                    facilityId: master.facilityId,
                                    status: "VERIFIED",
                                    verificationNotes: "Verified by inspector in follow-up review.",
                                  })
                                }
                                className="rounded-lg border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-700 hover:border-sky-300 hover:text-sky-700"
                              >
                                Verify
                              </button>
                            ) : null}
                          </div>
                        </div>
                        {action.completionNotes ? <p className="mt-3 text-sm text-gray-600">{action.completionNotes}</p> : null}
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            ) : null}

            {activeTab === "certificates" ? (
              <div className="grid gap-4 lg:grid-cols-[0.9fr_1.1fr]">
                <div className="rounded-2xl border border-gray-200 bg-white p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Certificate register</h3>
                  <div className="mt-4 space-y-3">
                    {profile.certificates.map((certificate) => (
                      <div key={certificate.certificateId} className="rounded-2xl border border-gray-200 p-4">
                        <div className="flex items-center justify-between gap-3">
                          <div>
                            <p className="font-medium text-gray-900">{certificate.certificateNumber}</p>
                            <p className="mt-1 text-xs text-gray-500">
                              {certificate.certificateType} • Issued {certificate.issueDate} • Expires {certificate.expiryDate ?? "Not set"}
                            </p>
                          </div>
                          <Stamp className="h-5 w-5 text-gray-400" />
                        </div>
                        <p className="mt-3 text-sm text-gray-600">
                          Authority: {formatLabel(certificate.issuedUnderAuthority)} • Status {formatLabel(certificate.status)}
                        </p>
                      </div>
                    ))}
                  </div>
                </div>
                <div className="rounded-2xl border border-gray-200 bg-white p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Committee log</h3>
                  <div className="mt-4 space-y-3">
                    {profile.committeeReviews.map((review) => (
                      <div key={review.reviewId} className="rounded-2xl border border-gray-200 p-4">
                        <div className="flex items-center justify-between gap-3">
                          <div>
                            <p className="font-medium text-gray-900">{formatLabel(review.decision)}</p>
                            <p className="mt-1 text-xs text-gray-500">
                              {review.committeeType} • {new Date(review.decidedAt).toLocaleDateString()}
                            </p>
                          </div>
                          <BadgeCheck className="h-5 w-5 text-gray-400" />
                        </div>
                        {review.notes ? <p className="mt-3 text-sm text-gray-600">{review.notes}</p> : null}
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            ) : null}

            {activeTab === "audit" ? (
              <div className="grid gap-4 lg:grid-cols-[0.9fr_1.1fr]">
                <div className="space-y-4">
                  <div className="rounded-2xl border border-gray-200 bg-white p-5">
                    <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Lifecycle history</h3>
                    <div className="mt-4 space-y-3">
                      {profile.statusHistory.map((entry) => (
                        <div key={`${entry.changedAt}-${entry.regulatoryStatus}`} className="rounded-2xl border border-gray-200 p-4">
                          <p className="font-medium text-gray-900">{formatLabel(entry.regulatoryStatus)}</p>
                          <p className="mt-1 text-xs text-gray-500">
                            {new Date(entry.changedAt).toLocaleString()} • {entry.changedBy} • {formatLabel(entry.authorityContext)}
                          </p>
                          {entry.reason ? <p className="mt-2 text-sm text-gray-600">{entry.reason}</p> : null}
                        </div>
                      ))}
                    </div>
                  </div>

                  <div className="rounded-2xl border border-gray-200 bg-white p-5">
                    <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Enforcement / closure recommendation</h3>
                    <form
                      className="mt-4 space-y-3"
                      onSubmit={(event) => {
                        event.preventDefault();
                        openEnforcementCase.mutate({
                          facilityId: master.facilityId,
                          triggerType: enforcementForm.triggerType,
                          status: enforcementForm.status,
                          recommendation: enforcementForm.recommendation,
                          authorityRoute: enforcementForm.authorityRoute,
                          decisionDetails: enforcementForm.decisionDetails,
                        });
                      }}
                    >
                      <input
                        value={enforcementForm.triggerType}
                        onChange={(event) => setEnforcementForm((current) => ({ ...current, triggerType: event.target.value }))}
                        className="w-full rounded-xl border border-gray-300 px-3 py-2.5 text-sm"
                      />
                      <select
                        value={enforcementForm.status}
                        onChange={(event) => setEnforcementForm((current) => ({ ...current, status: event.target.value }))}
                        className="w-full rounded-xl border border-gray-300 px-3 py-2.5 text-sm"
                      >
                        <option value="RECOMMENDED_RESTRICTION">Recommend restriction</option>
                        <option value="RECOMMENDED_SUSPENSION">Recommend suspension</option>
                        <option value="RECOMMENDED_CLOSURE">Recommend closure</option>
                      </select>
                      <textarea
                        rows={3}
                        value={enforcementForm.decisionDetails}
                        onChange={(event) => setEnforcementForm((current) => ({ ...current, decisionDetails: event.target.value }))}
                        className="w-full rounded-xl border border-gray-300 px-3 py-2.5 text-sm"
                        placeholder="Authority route, findings summary, and recommended action"
                      />
                      <button className="rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white hover:bg-rose-700" type="submit">
                        Open enforcement case
                      </button>
                    </form>
                  </div>
                </div>

                <div className="rounded-2xl border border-gray-200 bg-white p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Audit trail</h3>
                  <div className="mt-4 space-y-3">
                    {profile.auditTrail.map((entry) => (
                      <div key={entry.auditId} className="rounded-2xl border border-gray-200 p-4">
                        <div className="flex items-center justify-between gap-3">
                          <div>
                            <p className="font-medium text-gray-900">{formatLabel(entry.action)}</p>
                            <p className="mt-1 text-xs text-gray-500">
                              {entry.actorId} • {formatLabel(entry.actorType)} • {new Date(entry.createdAt).toLocaleString()}
                            </p>
                          </div>
                          <FileClock className="h-5 w-5 text-gray-400" />
                        </div>
                        <p className="mt-3 text-sm text-gray-600">
                          {entry.targetEntityType} {entry.targetEntityId}
                        </p>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            ) : null}

            {activeTab === "units" ? (
              <div className="grid gap-4 lg:grid-cols-2">
                <div className="rounded-2xl border border-gray-200 bg-white p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Registered units / departments</h3>
                  <div className="mt-4 space-y-3">
                    {profile.units.map((unit) => (
                      <div key={unit.unitId} className="rounded-2xl border border-gray-200 p-4">
                        <div className="flex items-center justify-between gap-3">
                          <div>
                            <p className="font-medium text-gray-900">{unit.name}</p>
                            <p className="mt-1 text-xs text-gray-500">
                              {formatLabel(unit.unitType)} • {formatLabel(unit.regulatoryStatus)}
                            </p>
                          </div>
                          <LayoutGrid className="h-5 w-5 text-gray-400" />
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
                <div className="rounded-2xl border border-gray-200 bg-white p-5">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">Practitioner in charge</h3>
                  <div className="mt-4 space-y-3">
                    {profile.practitionerAssignments.map((assignment) => (
                      <div key={assignment.assignmentId} className="rounded-2xl border border-gray-200 p-4">
                        <div className="flex items-center justify-between gap-3">
                          <div>
                            <p className="font-medium text-gray-900">{assignment.providerPublicId}</p>
                            <p className="mt-1 text-xs text-gray-500">
                              {formatLabel(assignment.role)} • {formatLabel(assignment.approvalState)}
                            </p>
                          </div>
                          <ShieldAlert className="h-5 w-5 text-gray-400" />
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            ) : null}

            {(createApplication.isPending ||
              submitApplication.isPending ||
              readyForInspection.isPending ||
              uploadDocument.isPending ||
              scheduleInspection.isPending ||
              recordInspection.isPending ||
              updateComplianceAction.isPending ||
              recordCommitteeDecision.isPending ||
              openEnforcementCase.isPending) ? (
              <div className="fixed bottom-6 right-6 rounded-full bg-gray-900 px-4 py-2 text-sm font-medium text-white shadow-xl">
                Working...
              </div>
            ) : null}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
