"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { ArrowLeft, Loader2, Plus, RefreshCw } from "lucide-react";
import {
  useCreateRenewalApplication,
  useIssueLicence,
  useOpenEnforcementCase,
  useScheduleInspection,
  useSiteRegistrySiteProfile,
  useUploadSiteDocument,
  useVerifySiteDocument,
  useCreateAssignment,
  useUpdateComplianceAction,
  useUpdateSiteLocation,
} from "@/hooks/queries/useSiteRegistry";
import { NdilaLocationPicker } from "@/components/ndila/NdilaLocationPicker";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function SiteRegistryProfilePage({ params }: { params: { siteId: string } }) {
  const siteId = decodeURIComponent(params.siteId);
  const profileQ = useSiteRegistrySiteProfile(siteId);

  const scheduleInspection = useScheduleInspection();
  const issueLicence = useIssueLicence();
  const createRenewal = useCreateRenewalApplication();
  const openCase = useOpenEnforcementCase();
  const updateAction = useUpdateComplianceAction();
  const uploadDoc = useUploadSiteDocument();
  const verifyDoc = useVerifySiteDocument();
  const createAssignment = useCreateAssignment();
  const updateLocation = useUpdateSiteLocation();

  const [scheduledDate, setScheduledDate] = useState(new Date().toISOString().slice(0, 10));
  const [siteCoords, setSiteCoords] = useState<{ latitude: number; longitude: number } | null>(null);
  const [inspectionType, setInspectionType] = useState("INITIAL");
  const [templateCode, setTemplateCode] = useState("");

  const profile = profileQ.data;
  const actions = useMemo(() => profile?.complianceActions ?? [], [profile]);
  const inspections = useMemo(() => profile?.inspections ?? [], [profile]);
  const licences = useMemo(() => profile?.licences ?? [], [profile]);
  const documents = useMemo(() => profile?.documents ?? [], [profile]);
  const assignments = useMemo(() => profile?.assignments ?? [], [profile]);

  const [docFile, setDocFile] = useState<File | null>(null);
  const [docType, setDocType] = useState("SITE_PHOTO");
  const [docNotes, setDocNotes] = useState("");

  return (
    <AppLayout>
      <PageShell
        title={profile?.site.name || "Indawo site profile"}
        subtitle="Premises lifecycle, inspections, licensing, and compliance actions."
        serviceSlug="indawo"
      >
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <Link href="/public-health/site-registry" className="inline-flex items-center gap-2 text-sm text-primary hover:underline">
            <ArrowLeft className="h-4 w-4" />
            Site registry
          </Link>
          <div className="mt-1 text-sm text-muted-foreground">
            <span className="font-mono text-xs">{siteId}</span>
            {profile?.site.siteCode ? <span className="ml-2 text-xs text-muted-foreground">· Code: {profile.site.siteCode}</span> : null}
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => profileQ.refetch()}
            className="inline-flex items-center gap-2 rounded-md border bg-card px-3 py-2 text-sm text-foreground hover:bg-background"
          >
            <RefreshCw className="h-4 w-4" />
            Refresh
          </button>
        </div>
      </div>

      {profileQ.isLoading ? (
        <div className="rounded-lg border bg-card p-6 text-sm text-muted-foreground">
          <div className="flex items-center gap-2">
            <Loader2 className="h-4 w-4 animate-spin" />
            Loading site profile…
          </div>
        </div>
      ) : profileQ.isError || !profile ? (
        <div className="rounded-lg border bg-card p-6 text-sm text-red-600">Failed to load site profile.</div>
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-3">
            <div className="rounded-lg border bg-card p-4">
              <div className="text-xs font-medium uppercase text-muted-foreground">Regulatory status</div>
              <div className="mt-1 text-sm font-semibold text-foreground">{profile.site.regulatoryStatus || "—"}</div>
              <div className="mt-2 text-xs text-muted-foreground">Lifecycle: {profile.site.lifecycleStatus || "—"}</div>
              <div className="mt-1 text-xs text-muted-foreground">Active: {profile.site.activeFlag ? "Yes" : "No"}</div>
            </div>
            <div className="rounded-lg border bg-card p-4">
              <div className="text-xs font-medium uppercase text-muted-foreground">Category</div>
              <div className="mt-1 text-sm font-semibold text-foreground">{profile.site.siteCategory || "—"}</div>
              <div className="mt-2 text-xs text-muted-foreground">Risk: {profile.site.riskClass || "—"}</div>
            </div>
            <div className="rounded-lg border bg-card p-4">
              <div className="text-xs font-medium uppercase text-muted-foreground">Jurisdiction</div>
              <div className="mt-1 text-sm font-semibold text-foreground">{profile.site.province || "—"}</div>
              <div className="mt-2 text-xs text-muted-foreground">
                {profile.site.district || "—"}
                {profile.site.ward ? ` · ${profile.site.ward}` : ""}
              </div>
            </div>
          </div>

          <div className="rounded-lg border bg-card p-4">
            <div className="text-sm font-medium text-foreground">Site coordinates</div>
            <p className="mt-1 text-xs text-muted-foreground">
              Capture or update the regulated site location for public-health maps and field routing.
            </p>
            <div className="mt-3">
              <NdilaLocationPicker
                purposeOfUse="PUBLIC_HEALTH_OPERATIONS"
                value={
                  siteCoords ??
                  (profile.site.latitude != null && profile.site.longitude != null
                    ? { latitude: profile.site.latitude, longitude: profile.site.longitude }
                    : null)
                }
                onChange={(next) => setSiteCoords(next)}
                verificationRequired
              />
            </div>
            <button
              type="button"
              disabled={updateLocation.isPending || !siteCoords}
              onClick={() => {
                if (!siteCoords) return;
                updateLocation.mutate({
                  siteId,
                  latitude: siteCoords.latitude,
                  longitude: siteCoords.longitude,
                  geocodeQuality: "MANUAL",
                });
              }}
              className="mt-3 inline-flex items-center gap-2 rounded-md bg-primary-hover px-3 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
            >
              {updateLocation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
              Save location
            </button>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <div className="rounded-lg border bg-card">
              <div className="flex items-center justify-between border-b px-4 py-3">
                <div className="text-sm font-medium text-foreground">Schedule inspection</div>
              </div>
              <div className="space-y-3 p-4">
                <div className="grid grid-cols-2 gap-3">
                  <label className="text-xs text-muted-foreground">
                    Type
                    <select
                      value={inspectionType}
                      onChange={(e) => setInspectionType(e.target.value)}
                      className="mt-1 w-full rounded-md border px-2 py-2 text-sm"
                    >
                      {["INITIAL", "ROUTINE", "FOLLOW_UP", "VERIFICATION", "INVESTIGATIVE", "SPECIAL"].map((t) => (
                        <option key={t} value={t}>
                          {t}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="text-xs text-muted-foreground">
                    Scheduled date
                    <input
                      type="date"
                      value={scheduledDate}
                      onChange={(e) => setScheduledDate(e.target.value)}
                      className="mt-1 w-full rounded-md border px-2 py-2 text-sm"
                    />
                  </label>
                </div>
                <label className="text-xs text-muted-foreground">
                  Checklist template code (optional)
                  <input
                    value={templateCode}
                    onChange={(e) => setTemplateCode(e.target.value)}
                    placeholder="e.g. FOOD_PREMISES_INITIAL"
                    className="mt-1 w-full rounded-md border px-2 py-2 text-sm"
                  />
                </label>
                <button
                  disabled={scheduleInspection.isPending}
                  onClick={() =>
                    scheduleInspection.mutate({
                      siteId,
                      inspectionType,
                      scheduledDate,
                      templateCode: templateCode || undefined,
                      inspectorRefs: [],
                    })
                  }
                  className="inline-flex items-center gap-2 rounded-md bg-primary-hover px-3 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
                >
                  <Plus className="h-4 w-4" />
                  Schedule
                </button>
                {scheduleInspection.isError ? (
                  <div className="text-xs text-red-600">Failed to schedule inspection.</div>
                ) : null}
              </div>
            </div>

            <div className="rounded-lg border bg-card">
              <div className="flex items-center justify-between border-b px-4 py-3">
                <div className="text-sm font-medium text-foreground">Licensing</div>
              </div>
              <div className="space-y-3 p-4">
                <button
                  disabled={issueLicence.isPending}
                  onClick={() =>
                    issueLicence.mutate({
                      siteId,
                      licenceType: "PUBLIC_HEALTH_PERMIT",
                      issueDate: new Date().toISOString().slice(0, 10),
                      expiryDate: new Date(new Date().setFullYear(new Date().getFullYear() + 1)).toISOString().slice(0, 10),
                    })
                  }
                  className="inline-flex items-center gap-2 rounded-md bg-neutral-900 px-3 py-2 text-sm font-medium text-white hover:bg-black disabled:opacity-50"
                >
                  Issue licence (1 year)
                </button>
                <button
                  disabled={createRenewal.isPending}
                  onClick={() => createRenewal.mutate({ siteId, applicantName: "Applicant" })}
                  className="inline-flex items-center gap-2 rounded-md border bg-card px-3 py-2 text-sm text-foreground hover:bg-background disabled:opacity-50"
                >
                  Create renewal application
                </button>
                {issueLicence.isError ? <div className="text-xs text-red-600">Failed to issue licence.</div> : null}
                {createRenewal.isError ? <div className="text-xs text-red-600">Failed to create renewal.</div> : null}
                <div className="mt-2 text-xs text-muted-foreground">
                  Licences: {licences.length} · Inspections: {inspections.length} · Actions: {actions.length}
                </div>
              </div>
            </div>
          </div>

          <div className="rounded-lg border bg-card">
            <div className="border-b px-4 py-3">
              <div className="text-sm font-medium text-foreground">Compliance actions</div>
            </div>
            <div className="divide-y">
              {actions.map((a) => {
                const actionId = String(a.actionId || "");
                return (
                  <div key={actionId} className="flex flex-col gap-2 px-4 py-3 md:flex-row md:items-center md:justify-between">
                    <div>
                      <div className="text-sm font-medium text-foreground">{String(a.actionType || "ACTION")}</div>
                      <div className="text-xs text-muted-foreground">
                        Status: {String(a.status || "—")} · Due: {String(a.dueDate || "—")}
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                        disabled={updateAction.isPending || !actionId}
                        onClick={() => updateAction.mutate({ actionId, body: { status: "COMPLETED", completionNotes: "Completed" } })}
                        className="rounded-md border bg-card px-3 py-2 text-xs text-foreground hover:bg-background disabled:opacity-50"
                      >
                        Mark completed
                      </button>
                    </div>
                  </div>
                );
              })}
              {actions.length === 0 ? <div className="px-4 py-6 text-sm text-muted-foreground">No compliance actions.</div> : null}
            </div>
          </div>

          <div className="rounded-lg border bg-card">
            <div className="flex items-center justify-between border-b px-4 py-3">
              <div className="text-sm font-medium text-foreground">Documents</div>
            </div>
            <div className="space-y-3 p-4">
              <div className="grid gap-3 md:grid-cols-3">
                <label className="text-xs text-muted-foreground">
                  Type
                  <input value={docType} onChange={(e) => setDocType(e.target.value)} className="mt-1 w-full rounded-md border px-2 py-2 text-sm" />
                </label>
                <label className="text-xs text-muted-foreground md:col-span-2">
                  Notes
                  <input value={docNotes} onChange={(e) => setDocNotes(e.target.value)} className="mt-1 w-full rounded-md border px-2 py-2 text-sm" />
                </label>
              </div>
              <input type="file" onChange={(e) => setDocFile(e.target.files?.[0] ?? null)} className="text-sm" />
              <button
                disabled={!docFile || uploadDoc.isPending}
                onClick={() => docFile && uploadDoc.mutate({ file: docFile, siteId, documentType: docType, notes: docNotes })}
                className="inline-flex items-center gap-2 rounded-md bg-primary-hover px-3 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
              >
                Upload document
              </button>
              {uploadDoc.isError ? <div className="text-xs text-red-600">Failed to upload document.</div> : null}
            </div>
            <div className="divide-y">
              {documents.map((d) => {
                const row = d as Record<string, unknown>;
                const documentId = String(row.documentId ?? "");
                const state = String(row.verificationState ?? "UNVERIFIED");
                return (
                  <div key={documentId} className="flex flex-col gap-2 px-4 py-3 md:flex-row md:items-center md:justify-between">
                    <div>
                      <div className="text-sm font-medium text-foreground">{String(row.documentType ?? "DOC")}</div>
                      <div className="text-xs text-muted-foreground">State: {state}</div>
                      <div className="text-[11px] text-muted-foreground font-mono">{String(row.fileReference ?? "")}</div>
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                        disabled={verifyDoc.isPending || !documentId}
                        onClick={() => verifyDoc.mutate({ documentId, verificationState: "VERIFIED", notes: "Verified" })}
                        className="rounded-md border bg-card px-3 py-2 text-xs text-foreground hover:bg-background disabled:opacity-50"
                      >
                        Mark verified
                      </button>
                    </div>
                  </div>
                );
              })}
              {documents.length === 0 ? <div className="px-4 py-6 text-sm text-muted-foreground">No documents.</div> : null}
            </div>
          </div>

          <div className="rounded-lg border bg-card">
            <div className="flex items-center justify-between border-b px-4 py-3">
              <div className="text-sm font-medium text-foreground">Assignments</div>
              <button
                disabled={createAssignment.isPending}
                onClick={() =>
                  createAssignment.mutate({
                    siteId,
                    assignmentType: "INSPECTION",
                    assignedRole: "INSPECTOR",
                    priority: "NORMAL",
                    notes: "Auto-assigned from UI",
                  })
                }
                className="rounded-md border bg-card px-3 py-2 text-xs text-foreground hover:bg-background disabled:opacity-50"
              >
                Create assignment
              </button>
            </div>
            <div className="divide-y">
              {assignments.map((a) => {
                const row = a as Record<string, unknown>;
                return (
                <div key={String(row.assignmentId ?? "")} className="px-4 py-3">
                  <div className="text-sm font-medium text-foreground">{String(row.assignmentType ?? "ASSIGNMENT")}</div>
                  <div className="text-xs text-muted-foreground">
                    Status: {String(row.status ?? "—")} · Role: {String(row.assignedRole ?? "—")} · Assignee:{" "}
                    {String(row.assignedToRef ?? "—")}
                  </div>
                </div>
              );
              })}
              {assignments.length === 0 ? <div className="px-4 py-6 text-sm text-muted-foreground">No assignments.</div> : null}
            </div>
          </div>

          <div className="rounded-lg border bg-card">
            <div className="flex items-center justify-between border-b px-4 py-3">
              <div className="text-sm font-medium text-foreground">Enforcement</div>
              <button
                disabled={openCase.isPending}
                onClick={() => openCase.mutate({ siteId, triggerType: "COMPLAINT", recommendation: "Investigate", authorityRoute: "EHO" })}
                className="rounded-md border bg-card px-3 py-2 text-xs text-foreground hover:bg-background disabled:opacity-50"
              >
                Open enforcement case
              </button>
            </div>
            <div className="px-4 py-6 text-sm text-muted-foreground">
              Foundation support only: cases can be opened and linked refs can be added later.
            </div>
          </div>
        </>
      )}
    </div>
      </PageShell>
    </AppLayout>
  );
}

